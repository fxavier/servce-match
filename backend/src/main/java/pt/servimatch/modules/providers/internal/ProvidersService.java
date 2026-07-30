package pt.servimatch.modules.providers.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.billing.SubscriptionLifecycle;
import pt.servimatch.modules.billing.SubscriptionPlan;
import pt.servimatch.modules.billing.SubscriptionStatus;
import pt.servimatch.modules.categories.CategoriesApi;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.providers.internal.web.GeoPointDto;
import pt.servimatch.modules.providers.internal.web.ProviderProfileDto;
import pt.servimatch.modules.providers.internal.web.ProviderZoneDto;
import pt.servimatch.modules.providers.internal.web.UpdateProviderProfileRequest;
import pt.servimatch.modules.uploads.ImageRef;
import pt.servimatch.modules.uploads.UploadPurpose;
import pt.servimatch.modules.uploads.UploadsApi;
import pt.servimatch.modules.users.UsersApi;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
class ProvidersService implements ProvidersApi {

    private final ProviderRepository repository;
    private final UsersApi usersApi;
    private final CategoriesApi categoriesApi;
    private final UploadsApi uploadsApi;
    private final SubscriptionLifecycle subscriptionLifecycle;

    ProvidersService(ProviderRepository repository, UsersApi usersApi, CategoriesApi categoriesApi,
                      UploadsApi uploadsApi, SubscriptionLifecycle subscriptionLifecycle) {
        this.repository = repository;
        this.usersApi = usersApi;
        this.categoriesApi = categoriesApi;
        this.uploadsApi = uploadsApi;
        this.subscriptionLifecycle = subscriptionLifecycle;
    }

    // ---------------------------------------------------------------- ProvidersApi (público, síncrono)

    @Override
    @Transactional
    public UUID ensureProvisioned(UUID userId) {
        Optional<ProviderProfileRow> existing = repository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        return repository.insertIfAbsent(userId)
                .orElseGet(() -> repository.findByUserId(userId)
                        .orElseThrow(() -> new IllegalStateException("Provisionamento JIT de provider falhou para user=" + userId))
                        .id());
    }

    @Override
    public Optional<UUID> findProviderIdByUserId(UUID userId) {
        return repository.findByUserId(userId).map(ProviderProfileRow::id);
    }

    @Override
    public Optional<UUID> findUserIdByProviderId(UUID providerId) {
        return repository.findById(providerId).map(ProviderProfileRow::userId);
    }

    @Override
    public Map<UUID, UUID> findUserIdsByProviderIds(Set<UUID> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByIds(providerIds).stream()
                .collect(Collectors.toMap(ProviderProfileRow::id, ProviderProfileRow::userId));
    }

    @Override
    public Optional<ProviderEligibility> checkEligibility(UUID providerId) {
        return repository.findById(providerId)
                .map(row -> new ProviderEligibility(
                        row.id(),
                        "APPROVED".equals(row.approvalStatus()),
                        subscriptionLifecycle.isVisibilityEligible(row.id())));
    }

    @Override
    public Set<UUID> workedCategoryIds(UUID providerId) {
        return repository.findWorkedCategoryIds(providerId);
    }

    @Override
    public Optional<ProviderSummaryView> summary(UUID providerId) {
        return repository.findById(providerId)
                .map(row -> toSummaryView(row, usersApi.findByIds(Set.of(row.userId()))));
    }

    @Override
    public Map<UUID, ProviderSummaryView> summaries(Set<UUID> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return Map.of();
        }
        List<ProviderProfileRow> rows = repository.findByIds(providerIds);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UsersApi.UserSummaryView> users = usersApi.findByIds(
                rows.stream().map(ProviderProfileRow::userId).collect(Collectors.toSet()));
        return rows.stream().collect(Collectors.toMap(ProviderProfileRow::id, row -> toSummaryView(row, users)));
    }

    // ---------------------------------------------------------------- Uso pelo controller deste módulo

    /**
     * {@code GET /v1/providers/{providerId}}: público, sem autenticação.
     * {@code 404} indistinguível para os três casos — prestador inexistente,
     * não {@code APPROVED}, não visível (subscrição sem grace ativo) —
     * lançado a partir do <b>mesmo</b> ponto com a <b>mesma</b> mensagem;
     * distinguir os casos seria um oráculo de enumeração num endpoint
     * público e indexável.
     */
    ProviderProfileDto getPublicProfile(UUID providerId) {
        ProviderProfileRow row = repository.findById(providerId).orElseThrow(this::notFoundPublic);
        boolean approved = "APPROVED".equals(row.approvalStatus());
        boolean visible = subscriptionLifecycle.isVisibilityEligible(row.id());
        if (!approved || !visible) {
            throw notFoundPublic();
        }
        return toProfileDto(row);
    }

    private org.springframework.web.ErrorResponseException notFoundPublic() {
        return Problems.notFound("Prestador não encontrado.");
    }

    /**
     * {@code GET /v1/providers/me}: role {@code PROVIDER}. O controlador
     * resolve {@code providerId} por {@link #ensureProvisioned} (não por
     * {@link #findProviderIdByUserId}) — decisão deliberada de consistência
     * com {@code GET /v1/providers/me/requests}, já implementado dessa
     * forma: um utilizador com a role mas sem perfil ainda ganha um perfil
     * vazio (approval PENDING) em vez de {@code 404} no seu próprio ecrã de
     * edição, que é precisamente o ecrã por onde ele o preencheria pela
     * primeira vez. Um {@code 404} aqui obrigaria a um endpoint adicional só
     * para criar o perfil antes de o poder ver — sem benefício.
     *
     * <p><b>Discrepância reportada ao {@code api-contract}:</b> o contrato
     * documenta {@code 404} como resposta possível deste endpoint (e de
     * {@code PUT}). Com esta decisão, esse {@code 404} fica inalcançável —
     * qualquer principal com role {@code PROVIDER} sempre tem (ou ganha na
     * chamada) um {@code provider_profile}. Não removido do contrato
     * unilateralmente (evolução aditiva, ADR-0008; e é o {@code api-contract}
     * que decide se o documenta como "reservado" ou o retira).
     */
    ProviderProfileDto getMyProfile(UUID providerId) {
        ProviderProfileRow row = repository.findById(providerId)
                .orElseThrow(() -> new IllegalStateException("Perfil de prestador inexistente após provisionamento: " + providerId));
        return toProfileDto(row);
    }

    /**
     * {@code PUT /v1/providers/me}: substituição total das três listas
     * editáveis. Toda a validação corre <b>antes</b> da primeira escrita —
     * categoria desconhecida, região desconhecida ou imagem de portefólio
     * não pertencente ao prestador nunca deixam a base de dados a meio de
     * uma substituição; qualquer falha aqui é {@code 4xx} (Problem Details),
     * nunca uma violação de FK a escapar como {@code 500}.
     *
     * <p>{@code verified}, {@code approval_status} e {@code rating_avg} não
     * são editáveis por este método: {@link UpdateProviderProfileRequest}
     * não os declara — não há nada para ignorar explicitamente porque o
     * corpo do pedido nunca os carrega (ver o seu javadoc).
     */
    @Transactional
    ProviderProfileDto updateMyProfile(UUID providerId, UpdateProviderProfileRequest request) {
        ProviderProfileRow current = repository.findById(providerId)
                .orElseThrow(() -> new IllegalStateException("Perfil de prestador inexistente após provisionamento: " + providerId));

        Set<UUID> categoryIds = request.categoryIds() == null ? Set.of() : new LinkedHashSet<>(request.categoryIds());
        for (UUID categoryId : categoryIds) {
            categoriesApi.findById(categoryId)
                    .orElseThrow(() -> Problems.unprocessable("Categoria desconhecida: " + categoryId));
        }

        Set<String> regionCodes = request.regionCodes() == null ? Set.of() : new LinkedHashSet<>(request.regionCodes());
        for (String regionCode : regionCodes) {
            if (!RegionCatalog.isKnown(regionCode)) {
                throw Problems.unprocessable("Região desconhecida: " + regionCode);
            }
        }

        List<UUID> portfolioImageIds = request.portfolioImageIds() == null ? List.of() : request.portfolioImageIds();
        for (UUID imageId : portfolioImageIds) {
            // Valida posse + purpose (e, na primeira confirmação, magic bytes —
            // CLAUDE.md §4); lança 422 (ErrorResponseException) em qualquer
            // falha, mesmo padrão de RequestsService.createDraft — não
            // precisamos de tratar aqui, o GlobalExceptionHandler central já
            // produz RFC 9457. Corre para todos os ids ANTES de qualquer
            // escrita nossa (DELETE/INSERT abaixo).
            uploadsApi.confirmOwnedUpload(imageId, current.userId(), UploadPurpose.PROVIDER_PORTFOLIO);
        }

        // Só agora a primeira escrita — toda a validação (categorias, regiões,
        // posse das imagens) já passou.
        repository.updateEditableFields(providerId, request.headline(), request.bio());
        repository.replaceCategories(providerId, categoryIds);
        repository.replaceAdminRegionZones(providerId, regionCodes);
        repository.replacePortfolio(providerId, portfolioImageIds);

        ProviderProfileRow updated = repository.findById(providerId).orElseThrow();
        return toProfileDto(updated);
    }

    // ---------------------------------------------------------------- mapeamento

    private ProviderSummaryView toSummaryView(ProviderProfileRow row, Map<UUID, UsersApi.UserSummaryView> users) {
        String displayName = Optional.ofNullable(users.get(row.userId()))
                .map(UsersApi.UserSummaryView::displayName)
                .orElse(row.headline() == null ? "Prestador" : row.headline());
        return new ProviderSummaryView(
                row.id(),
                displayName,
                row.headline(),
                row.companyName(),
                row.ratingAvg().doubleValue(),
                row.ratingCount(),
                row.verified(),
                premiumBadge(row.id()),
                avatarUrl(row.avatarAssetId()));
    }

    private ProviderProfileDto toProfileDto(ProviderProfileRow row) {
        ProviderSummaryView summaryView = toSummaryView(row, usersApi.findByIds(Set.of(row.userId())));

        List<String> categoryNames = repository.findWorkedCategoryIds(row.id()).stream()
                .map(categoriesApi::findById)
                .flatMap(Optional::stream)
                .map(CategoriesApi.CategoryView::name)
                .sorted()
                .toList();

        List<ProviderZoneDto> zones = repository.findAdminRegionCodes(row.id()).stream()
                .map(code -> new ProviderZoneDto(code, RegionCatalog.labelFor(code).orElse(code)))
                .toList();

        GeoPointDto location = row.locationLat() != null && row.locationLon() != null
                ? new GeoPointDto(row.locationLat(), row.locationLon())
                : null;

        Map<String, Integer> ratingDistribution = toRatingDistributionMap(repository.ratingDistribution(row.userId()));

        return new ProviderProfileDto(
                summaryView.id(),
                summaryView.displayName(),
                summaryView.headline(),
                summaryView.companyName(),
                summaryView.ratingAvg(),
                summaryView.ratingCount(),
                summaryView.verified(),
                summaryView.premiumBadge(),
                summaryView.avatarUrl(),
                row.bio(),
                categoryNames,
                zones,
                location,
                ratingDistribution,
                resolvePortfolioUrls(row.id()),
                row.createdAt());
    }

    private static Map<String, Integer> toRatingDistributionMap(RatingDistributionRow distribution) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("5", distribution.fiveStars());
        map.put("4", distribution.fourStars());
        map.put("3", distribution.threeStars());
        map.put("2", distribution.twoStars());
        map.put("1", distribution.oneStar());
        return map;
    }

    /**
     * Resolve {@code provider_portfolio(provider_id, image_asset_id, position)}
     * para URLs de leitura assinadas e frescas via {@code UploadsApi#resolve}
     * — nunca lendo {@code upload_asset} diretamente (ADR-0010). Mesma forma
     * de {@code RequestsService#resolveImages}: junta de novo por
     * {@code imageId}, preservando {@code position}; um {@code imageId} que
     * {@code resolve} omitir (não confirmado/expirado/desconhecido) fica de
     * fora do resultado, sem erro.
     */
    private List<String> resolvePortfolioUrls(UUID providerId) {
        List<PortfolioImageRow> links = repository.findPortfolio(providerId);
        if (links.isEmpty()) {
            return List.of();
        }
        Map<UUID, ImageRef> resolved = uploadsApi.resolve(links.stream().map(PortfolioImageRow::imageAssetId).toList())
                .stream()
                .collect(Collectors.toMap(ImageRef::id, ref -> ref));
        return links.stream()
                .map(link -> resolved.get(link.imageAssetId()))
                .filter(Objects::nonNull)
                .map(ImageRef::url)
                .toList();
    }

    private String avatarUrl(UUID avatarAssetId) {
        if (avatarAssetId == null) {
            return null;
        }
        List<ImageRef> resolved = uploadsApi.resolve(List.of(avatarAssetId));
        return resolved.isEmpty() ? null : resolved.get(0).url();
    }

    /**
     * {@code premiumBadge} deriva do plano da subscrição atual
     * ({@code subscription_plan.has_badge}, mesma fonte usada por
     * {@code search.ProviderSearchRepository}) — nunca fixo a {@code false}
     * como antes desta revisão (ADR-0011 D9 regista essa divergência como
     * achado; resolvida aqui porque este módulo já passou a depender de
     * {@code billing} por outra razão, D3). Só concede o emblema quando a
     * subscrição está no mesmo conjunto de estados que concede visibilidade
     * ({@code ACTIVE}/{@code PAST_DUE}) — uma subscrição {@code PENDING}
     * (nunca chegou a pagar) não mostra emblema de plano pago.
     */
    private boolean premiumBadge(UUID providerId) {
        return subscriptionLifecycle.findByProvider(providerId)
                .filter(subscription -> subscription.status() == SubscriptionStatus.ACTIVE
                        || subscription.status() == SubscriptionStatus.PAST_DUE)
                .flatMap(subscription -> subscriptionLifecycle.findPlan(subscription.planId()))
                .map(SubscriptionPlan::hasBadge)
                .orElse(false);
    }
}
