package pt.servimatch.modules.requests.internal;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.categories.CategoriesApi;
import pt.servimatch.modules.geo.GeoPoint;
import pt.servimatch.modules.matching.EligibilityQuery;
import pt.servimatch.modules.matching.MatchingApi;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.requests.RequestsApi;
import pt.servimatch.modules.uploads.ImageRef;
import pt.servimatch.modules.uploads.UploadPurpose;
import pt.servimatch.modules.uploads.UploadsApi;
import pt.servimatch.modules.requests.ServiceRequestStatus;
import pt.servimatch.modules.requests.RequestPublished;
import pt.servimatch.modules.requests.UrgencyLevel;
import pt.servimatch.modules.requests.internal.web.AddressDto;
import pt.servimatch.modules.requests.internal.web.CategoryDto;
import pt.servimatch.modules.requests.internal.web.CreateServiceRequestRequest;
import pt.servimatch.modules.requests.internal.web.GeoPointDto;
import pt.servimatch.modules.requests.internal.web.ImageRefDto;
import pt.servimatch.modules.requests.internal.web.PageMetaDto;
import pt.servimatch.modules.requests.internal.web.ServiceRequestDto;
import pt.servimatch.modules.requests.internal.web.ServiceRequestPageDto;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository.
@Service
@Lazy
class RequestsService implements RequestsApi {

    private final RequestRepository repository;
    private final CategoriesApi categoriesApi;
    private final UploadAssetLinker uploadAssetLinker;
    private final ApplicationEventPublisher events;
    private final ProvidersApi providersApi;
    private final MatchingApi matchingApi;
    private final UploadsApi uploadsApi;

    RequestsService(RequestRepository repository, CategoriesApi categoriesApi,
                     UploadAssetLinker uploadAssetLinker, ApplicationEventPublisher events,
                     ProvidersApi providersApi, MatchingApi matchingApi, UploadsApi uploadsApi) {
        this.repository = repository;
        this.categoriesApi = categoriesApi;
        this.uploadAssetLinker = uploadAssetLinker;
        this.events = events;
        this.providersApi = providersApi;
        this.matchingApi = matchingApi;
        this.uploadsApi = uploadsApi;
    }

    // ---------------------------------------------------------------- RequestsApi (público, síncrono)

    @Override
    public Optional<RequestSummary> get(UUID requestId) {
        return repository.findById(requestId).map(this::toSummary);
    }

    @Override
    public Map<UUID, String> findTitlesByIds(java.util.Collection<UUID> requestIds) {
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        return repository.findTitlesByIds(requestIds);
    }

    @Override
    @Transactional
    public void markInNegotiation(UUID requestId) {
        if (repository.markInNegotiation(requestId)) {
            return;
        }
        ServiceRequestRow row = repository.findById(requestId)
                .orElseThrow(() -> Problems.notFound("Pedido não encontrado."));
        if (!"IN_NEGOTIATION".equals(row.status())) {
            throw Problems.conflict("Pedido não está num estado que aceite propostas (status atual: " + row.status() + ").");
        }
        // já IN_NEGOTIATION: idempotente, nada a fazer.
    }

    @Override
    @Transactional
    public void confirm(UUID requestId) {
        if (repository.confirm(requestId)) {
            return;
        }
        repository.findById(requestId).orElseThrow(() -> Problems.notFound("Pedido não encontrado."));
        throw Problems.conflict("Pedido já não está em negociação (foi confirmado ou cancelado entretanto).");
    }

    // ---------------------------------------------------------------- Uso pelo controller deste módulo

    @Transactional
    public ServiceRequestDto createDraft(UUID customerId, CreateServiceRequestRequest request) {
        CategoriesApi.CategoryView category = categoriesApi.findActiveById(request.categoryId())
                .orElseThrow(() -> Problems.unprocessable("Categoria não encontrada ou inativa."));

        AddressDto address = request.address();
        GeoPointDto location = address.location();

        RequestRepository.NewRequest newRequest = new RequestRepository.NewRequest(
                customerId,
                category.id(),
                request.title(),
                request.description(),
                address.line1(),
                address.line2(),
                address.postalCode(),
                address.city(),
                address.regionCode(),
                address.country() == null || address.country().isBlank() ? "PT" : address.country(),
                location == null ? null : location.lat(),
                location == null ? null : location.lon(),
                (request.urgency() == null ? UrgencyLevel.NORMAL : request.urgency()).name(),
                request.availability());

        UUID requestId = repository.insertDraft(newRequest);

        List<UUID> imageIds = request.imageIds() == null ? List.of() : request.imageIds();
        if (!imageIds.isEmpty()) {
            // confirmOwnedUpload valida posse e purpose, e faz a verificação real
            // por magic bytes (CLAUDE.md §4) na primeira confirmação; lança 422
            // sozinha (ErrorResponseException) em qualquer falha — não precisamos
            // de tratar aqui, o GlobalExceptionHandler central já produz RFC 9457.
            for (UUID imageId : imageIds) {
                uploadsApi.confirmOwnedUpload(imageId, customerId, UploadPurpose.REQUEST_ATTACHMENT);
            }
            uploadAssetLinker.linkToRequest(requestId, imageIds);
        }

        ServiceRequestRow row = repository.findById(requestId).orElseThrow();
        // Autor a criar o próprio rascunho: sempre morada exata.
        return toDto(row, AddressExposure.EXACT);
    }

    /**
     * {@code GET /v1/requests} — pedidos do cliente autenticado
     * (ARQUITETURA §11.1). Dono resolvido a partir do {@code sub} do JWT
     * pelo controlador; filtro por dono <b>sempre em SQL</b>
     * ({@link RequestRepository#findPageForCustomer}), nunca em memória —
     * isolamento entre clientes (CLAUDE.md/relatório de entrega). Morada
     * sempre {@link AddressExposure#EXACT}: a página é sempre do próprio
     * dono, nunca de outro cliente nem de um prestador.
     *
     * <p>{@code statusFilter} não reconhecido é {@code 400} ({@link
     * Problems#badRequest}), nunca degradado para página vazia — uma lista
     * vazia silenciosa faria o cliente acreditar que não tem pedidos.
     */
    public ServiceRequestPageDto listMine(UUID customerId, String statusFilter, String cursor, int limit) {
        // Sem allowlist: o dono do pedido pode legitimamente listar qualquer
        // estado seu, incluindo DRAFT — só a validação de pertença ao enum
        // se aplica aqui (ver javadoc de {@link #parseStatusFilter}).
        String status = parseStatusFilter(statusFilter, null);
        CursorCodec.Position after = CursorCodec.decode(cursor).orElse(null);

        List<ServiceRequestRow> rows = repository.findPageForCustomer(customerId, status, after, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ServiceRequestRow> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore
                ? CursorCodec.encode(page.get(page.size() - 1).createdAt(), page.get(page.size() - 1).id())
                : null;

        return new ServiceRequestPageDto(toDtoPage(page, AddressExposure.EXACT), new PageMetaDto(nextCursor));
    }

    /**
     * Allowlist de {@code status} aceite em {@code GET
     * /v1/providers/me/requests} (ver {@link #listInbox}): {@code DRAFT}
     * nunca é um estado que um prestador possa pedir — nem sequer para o
     * rejeitar com {@code 403}, que seria um oráculo de existência. É
     * {@code 400}, porque o estado pedido é inválido para este endpoint,
     * independentemente de quem pergunta (CLAUDE.md/relatório de entrega,
     * defeito C4 — IDOR: {@code listInbox} devolvia {@code DRAFT} de
     * qualquer cliente, incluindo morada completa, porque o {@code status}
     * cru chegava direto à consulta sem validação).
     */
    private static final Set<ServiceRequestStatus> INBOX_ALLOWED_STATUSES =
            EnumSet.of(ServiceRequestStatus.PUBLISHED, ServiceRequestStatus.IN_NEGOTIATION);

    /**
     * Valida {@code raw} contra o enum {@link ServiceRequestStatus} e,
     * quando fornecido, contra uma allowlist adicional mais restrita do que
     * o enum inteiro — reutilizado por {@link #listMine} (sem allowlist: o
     * dono vê qualquer estado seu) e {@link #listInbox} (allowlist {@code
     * {PUBLISHED, IN_NEGOTIATION}}: o que o prestador pode legitimamente
     * pedir é sempre um subconjunto mais apertado do que o que o cliente
     * pode pedir sobre os seus próprios pedidos, nunca um método de
     * validação novo — a regra é "que estados este viewer pode pedir",
     * não "é um valor de enum válido"). Fora do enum ou fora da allowlist:
     * mesmo {@code 400} ({@link Problems#badRequest}) — nunca degradado
     * para página vazia silenciosa, e nunca {@code 403}: o estado pedido é
     * inválido para este endpoint, não é uma questão de permissão sobre um
     * recurso concreto.
     */
    private static String parseStatusFilter(String raw, Set<ServiceRequestStatus> allowlist) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ServiceRequestStatus status;
        try {
            status = ServiceRequestStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw Problems.badRequest("Valor de 'status' inválido: '" + raw + "'.");
        }
        if (allowlist != null && !allowlist.contains(status)) {
            throw Problems.badRequest("Valor de 'status' inválido: '" + raw + "'.");
        }
        return status.name();
    }

    /**
     * {@code providerId} é {@code null} quando o utilizador autenticado não
     * tem perfil de prestador (cliente puro) — nesse caso só {@code isOwner}
     * ou {@code isAdmin} podem dar acesso. Quando presente, a "vista
     * limitada" do contrato (getRequest: "prestadores elegíveis veem uma
     * vista limitada") é decidida pelo predicado central de elegibilidade
     * (ADR-0004 §10.3, {@link MatchingApi#isEligible}) — subscrição ativa,
     * aprovação, categoria trabalhada e cobertura geográfica — e não apenas
     * por o pedido não estar em {@code DRAFT}, que era a falha (fuga de
     * dados a todos os pedidos publicados do país, ver relatório de
     * entrega).
     */
    public ServiceRequestDto getForViewer(UUID requestId, UUID viewerUserId, UUID providerId, boolean isAdmin) {
        ServiceRequestRow row = repository.findById(requestId)
                .orElseThrow(() -> Problems.notFound("Pedido não encontrado."));
        boolean isOwner = row.customerId().equals(viewerUserId);
        if (isOwner || isAdmin) {
            return toDto(row, AddressExposure.EXACT);
        }
        if (providerId != null && !"DRAFT".equals(row.status()) && isEligibleProvider(providerId, row)) {
            return toDto(row, AddressExposure.ZONE);
        }
        throw Problems.forbidden("Sem permissão para ver este pedido.");
    }

    private boolean isEligibleProvider(UUID providerId, ServiceRequestRow row) {
        GeoPoint point = row.latitude() != null && row.longitude() != null
                ? new GeoPoint(row.latitude(), row.longitude())
                : null;
        return matchingApi.isEligible(new EligibilityQuery(providerId, row.categoryId(), point, row.addressRegionCode()));
    }

    @Transactional
    public ServiceRequestDto publish(UUID requestId, UUID ownerUserId) {
        ServiceRequestRow current = repository.findById(requestId)
                .orElseThrow(() -> Problems.notFound("Pedido não encontrado."));
        if (!current.customerId().equals(ownerUserId)) {
            throw Problems.forbidden("Só o dono do pedido o pode publicar.");
        }
        ServiceRequestRow published = repository.publish(requestId)
                .orElseThrow(() -> Problems.conflict("Pedido já não está em DRAFT (status atual: " + current.status() + ")."));

        events.publishEvent(new RequestPublished(
                published.id(), published.customerId(), published.categoryId(),
                published.latitude(), published.longitude(), published.addressRegionCode(),
                Objects.requireNonNullElse(published.publishedAt(), Instant.now())));

        // Dono a publicar o próprio pedido: sempre morada exata.
        return toDto(published, AddressExposure.EXACT);
    }

    /**
     * {@code GET /v1/providers/me/requests} — só pedidos elegíveis
     * (ARQUITETURA §11.2, CLAUDE.md: "gating por subscrição... verificado no
     * servidor, sempre"). Antes desta correção devolvia todos os pedidos
     * publicados do país a qualquer prestador subscrito, sem filtro de
     * categoria nem geografia (ver relatório de entrega — fuga de dados).
     *
     * <p>Duas fases: (1) restringe candidatos às categorias trabalhadas pelo
     * prestador ({@link ProvidersApi#workedCategoryIds}) diretamente na
     * consulta paginada — {@link MatchingApi#filterEligibleRequestIds} exige
     * esse pré-filtro, só cobre geografia (ver o seu javadoc); (2) filtra a
     * página resultante por cobertura geográfica, num único lote
     * <em>set-based</em>, nunca pedido a pedido. O cursor de continuação usa
     * sempre a última linha candidata (antes do filtro geográfico), para que
     * a paginação percorra de forma estável todos os candidatos por
     * categoria — uma página pode legitimamente devolver menos de
     * {@code limit} itens quando parte dos candidatos não é geograficamente
     * elegível.
     *
     * <p><b>Morada:</b> o viewer é sempre um prestador aqui — nunca o dono
     * do pedido — por isso a página inteira é {@link AddressExposure#ZONE}
     * (ver nota de auditoria em {@link #getForViewer}).
     *
     * <p><b>{@code status}:</b> validado por {@link #parseStatusFilter} contra
     * {@link #INBOX_ALLOWED_STATUSES} ({@code {PUBLISHED, IN_NEGOTIATION}}).
     * Antes desta correção o parâmetro chegava cru à consulta — {@code
     * ?status=DRAFT} devolvia pedidos ainda não publicados de qualquer
     * cliente, incluindo morada completa e código postal (defeito C4, IDOR;
     * ver relatório de entrega). Fora da allowlist é sempre {@code 400}
     * ({@link Problems#badRequest}), nunca {@code 403}: o estado pedido é
     * inválido para este endpoint, não é uma questão de permissão sobre um
     * pedido concreto.
     */
    public ServiceRequestPageDto listInbox(UUID providerId, String statusFilter, String cursor, int limit) {
        // Validar o parâmetro de entrada antes de qualquer acesso a dados: um
        // 400 por 'status' inválido nunca deve depender de o prestador ter
        // categorias associadas ou não — senão o mesmo pedido dá 400 ou 200
        // consoante o estado de outro dado, o que é uma inconsistência de API
        // desnecessária (e, em teoria, um bit de informação sobre o perfil do
        // prestador).
        String status = parseStatusFilter(statusFilter, INBOX_ALLOWED_STATUSES);

        Set<UUID> workedCategoryIds = providersApi.workedCategoryIds(providerId);
        if (workedCategoryIds.isEmpty()) {
            return new ServiceRequestPageDto(List.of(), new PageMetaDto(null));
        }

        List<String> statuses = status != null
                ? List.of(status)
                : List.of(ServiceRequestStatus.PUBLISHED.name(), ServiceRequestStatus.IN_NEGOTIATION.name());
        CursorCodec.Position after = CursorCodec.decode(cursor).orElse(null);

        List<ServiceRequestRow> rows = repository.findPage(statuses, List.copyOf(workedCategoryIds), after, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ServiceRequestRow> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore
                ? CursorCodec.encode(page.get(page.size() - 1).createdAt(), page.get(page.size() - 1).id())
                : null;

        Set<UUID> candidateIds = page.stream().map(ServiceRequestRow::id).collect(Collectors.toSet());
        Set<UUID> eligibleIds = matchingApi.filterEligibleRequestIds(providerId, candidateIds);
        List<ServiceRequestRow> eligiblePage = page.stream().filter(row -> eligibleIds.contains(row.id())).toList();

        return new ServiceRequestPageDto(toDtoPage(eligiblePage, AddressExposure.ZONE), new PageMetaDto(nextCursor));
    }

    // ---------------------------------------------------------------- mapeamento

    private RequestSummary toSummary(ServiceRequestRow row) {
        return new RequestSummary(row.id(), row.customerId(), row.categoryId(), ServiceRequestStatus.valueOf(row.status()));
    }

    private ServiceRequestDto toDto(ServiceRequestRow row, AddressExposure exposure) {
        CategoryDto category = categoriesApi.findById(row.categoryId())
                .map(c -> new CategoryDto(c.id(), c.parentId(), c.slug(), c.name(), c.active()))
                .orElse(null);

        AddressDto address = exposure.apply(row);
        List<ImageRefDto> images = resolveImages(row.id());

        return new ServiceRequestDto(
                row.id(), row.customerId(), category, row.title(), row.description(), address,
                row.urgency(), row.availability(), row.status(), images,
                // proposalCount não é computado nesta onda (ver relatório de entrega).
                null,
                row.createdAt(), row.publishedAt());
    }

    /**
     * Variante em página de {@link #toDto(ServiceRequestRow, AddressExposure)}
     * usada por {@link #listMine} e {@link #listInbox}: resolve categorias e
     * imagens para a página inteira em vez de por linha (CLAUDE.md — "nunca
     * uma por pedido").
     *
     * <p>Imagens: uma única consulta a {@code request_image} para todos os
     * {@code requestId} da página ({@link UploadAssetLinker#findByRequestIds})
     * seguida de uma única chamada a {@link UploadsApi#resolve} para o
     * conjunto de {@code imageId} de toda a página.
     *
     * <p>Categorias: {@link CategoriesApi} ainda não expõe um lote (só
     * {@code findById} por id — achado, não decisão deliberada; ver
     * relatório de entrega, "lacunas de contrato/API"). Como mitigação
     * dentro deste módulo, a resolução é memorizada por {@code categoryId}
     * <em>dentro desta chamada</em>: no pior caso (todas as linhas com
     * categorias diferentes) continua a ser uma chamada por categoria
     * distinta, nunca por linha — mas para o caso comum (poucas categorias
     * na página) elimina a repetição. Uma consulta verdadeiramente única
     * exige {@code CategoriesApi.findByIds(Set<UUID>)}, a pedir ao dono do
     * módulo {@code categories}.
     */
    private List<ServiceRequestDto> toDtoPage(List<ServiceRequestRow> rows, AddressExposure exposure) {
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<UUID, CategoryDto> categoriesById = new LinkedHashMap<>();
        for (ServiceRequestRow row : rows) {
            categoriesById.computeIfAbsent(row.categoryId(), id -> categoriesApi.findById(id)
                    .map(c -> new CategoryDto(c.id(), c.parentId(), c.slug(), c.name(), c.active()))
                    .orElse(null));
        }

        Set<UUID> requestIds = rows.stream().map(ServiceRequestRow::id).collect(Collectors.toSet());
        Map<UUID, List<RequestImageRow>> imagesByRequest = uploadAssetLinker.findByRequestIds(requestIds);
        Set<UUID> allImageAssetIds = imagesByRequest.values().stream()
                .flatMap(List::stream)
                .map(RequestImageRow::imageAssetId)
                .collect(Collectors.toSet());
        Map<UUID, ImageRef> resolvedImages = allImageAssetIds.isEmpty()
                ? Map.of()
                : uploadsApi.resolve(allImageAssetIds).stream().collect(Collectors.toMap(ImageRef::id, ref -> ref));

        return rows.stream()
                .map(row -> {
                    AddressDto address = exposure.apply(row);
                    List<ImageRefDto> images = imagesByRequest.getOrDefault(row.id(), List.of()).stream()
                            .map(link -> resolvedImages.get(link.imageAssetId()))
                            .filter(Objects::nonNull)
                            .map(ref -> new ImageRefDto(ref.id(), ref.url(), ref.contentType()))
                            .toList();
                    return new ServiceRequestDto(
                            row.id(), row.customerId(), categoriesById.get(row.categoryId()), row.title(), row.description(),
                            address, row.urgency(), row.availability(), row.status(), images,
                            null, row.createdAt(), row.publishedAt());
                })
                .toList();
    }

    /**
     * Resolve {@code request_image(request_id, image_asset_id, position)}
     * para URLs de leitura assinadas e frescas via {@code UploadsApi#resolve}
     * — nunca lendo {@code upload_asset} diretamente (ADR-0010, fechado
     * nesta onda). Junta de novo por {@code imageId}, preservando
     * {@code position}; {@code imageId} que {@code resolve} omitir (não
     * confirmado/expirado/desconhecido) fica de fora do resultado, sem erro.
     *
     * <p>Usado só pelos caminhos de item único ({@link #createDraft},
     * {@link #getForViewer}, {@link #publish}); páginas usam
     * {@link #toDtoPage} para evitar N+1.
     */
    private List<ImageRefDto> resolveImages(UUID requestId) {
        List<RequestImageRow> links = uploadAssetLinker.findByRequestId(requestId);
        if (links.isEmpty()) {
            return List.of();
        }
        Map<UUID, ImageRef> resolved = uploadsApi.resolve(links.stream().map(RequestImageRow::imageAssetId).toList())
                .stream()
                .collect(Collectors.toMap(ImageRef::id, ref -> ref));
        return links.stream()
                .map(link -> resolved.get(link.imageAssetId()))
                .filter(Objects::nonNull)
                .map(ref -> new ImageRefDto(ref.id(), ref.url(), ref.contentType()))
                .toList();
    }
}
