package pt.servimatch.modules.requests.internal;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.requests.RequestsApi;
import pt.servimatch.modules.requests.ServiceRequestStatus;
import pt.servimatch.modules.requests.RequestPublished;
import pt.servimatch.modules.requests.internal.web.AddressDto;
import pt.servimatch.modules.requests.internal.web.CategoryDto;
import pt.servimatch.modules.requests.internal.web.CreateServiceRequestRequest;
import pt.servimatch.modules.requests.internal.web.GeoPointDto;
import pt.servimatch.modules.requests.internal.web.ImageRefDto;
import pt.servimatch.modules.requests.internal.web.PageMetaDto;
import pt.servimatch.modules.requests.internal.web.ServiceRequestDto;
import pt.servimatch.modules.requests.internal.web.ServiceRequestPageDto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository.
@Service
@Lazy
class RequestsService implements RequestsApi {

    private final RequestRepository repository;
    private final CategoryLookup categoryLookup;
    private final UploadAssetLinker uploadAssetLinker;
    private final ApplicationEventPublisher events;

    RequestsService(RequestRepository repository, CategoryLookup categoryLookup,
                     UploadAssetLinker uploadAssetLinker, ApplicationEventPublisher events) {
        this.repository = repository;
        this.categoryLookup = categoryLookup;
        this.uploadAssetLinker = uploadAssetLinker;
        this.events = events;
    }

    // ---------------------------------------------------------------- RequestsApi (público, síncrono)

    @Override
    public Optional<RequestSummary> get(UUID requestId) {
        return repository.findById(requestId).map(this::toSummary);
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
        CategoryRow category = categoryLookup.findActiveById(request.categoryId())
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
                request.urgency() == null ? "NORMAL" : request.urgency(),
                request.availability());

        UUID requestId = repository.insertDraft(newRequest);

        List<UUID> imageIds = request.imageIds() == null ? List.of() : request.imageIds();
        if (!imageIds.isEmpty()) {
            for (UUID imageId : imageIds) {
                boolean owned = uploadAssetLinker.confirmOwnedPending(imageId, customerId, "REQUEST_ATTACHMENT");
                if (!owned) {
                    throw Problems.unprocessable("Imagem " + imageId + " não encontrada, não pertence ao utilizador ou tem finalidade incorreta.");
                }
            }
            uploadAssetLinker.linkToRequest(requestId, imageIds);
        }

        ServiceRequestRow row = repository.findById(requestId).orElseThrow();
        return toDto(row);
    }

    public ServiceRequestDto getForViewer(UUID requestId, UUID viewerUserId, boolean isOwnerCapable, boolean isAdmin) {
        ServiceRequestRow row = repository.findById(requestId)
                .orElseThrow(() -> Problems.notFound("Pedido não encontrado."));
        boolean isOwner = row.customerId().equals(viewerUserId);
        boolean visibleToProvider = isOwnerCapable && !"DRAFT".equals(row.status());
        if (!isOwner && !isAdmin && !visibleToProvider) {
            throw Problems.forbidden("Sem permissão para ver este pedido.");
        }
        return toDto(row);
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

        return toDto(published);
    }

    /** listProviderInbox — sem filtro geográfico/categoria (ver relatório de entrega). */
    public ServiceRequestPageDto listInbox(String statusFilter, String cursor, int limit) {
        List<String> statuses = statusFilter != null
                ? List.of(statusFilter)
                : List.of(ServiceRequestStatus.PUBLISHED.name(), ServiceRequestStatus.IN_NEGOTIATION.name());
        CursorCodec.Position after = CursorCodec.decode(cursor).orElse(null);

        List<ServiceRequestRow> rows = repository.findPage(statuses, after, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ServiceRequestRow> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore
                ? CursorCodec.encode(page.get(page.size() - 1).createdAt(), page.get(page.size() - 1).id())
                : null;

        return new ServiceRequestPageDto(page.stream().map(this::toDto).toList(), new PageMetaDto(nextCursor));
    }

    // ---------------------------------------------------------------- mapeamento

    private RequestSummary toSummary(ServiceRequestRow row) {
        return new RequestSummary(row.id(), row.customerId(), row.categoryId(), ServiceRequestStatus.valueOf(row.status()));
    }

    private ServiceRequestDto toDto(ServiceRequestRow row) {
        CategoryDto category = categoryLookup.findById(row.categoryId())
                .map(c -> new CategoryDto(c.id(), c.parentId(), c.slug(), c.name(), c.active()))
                .orElse(null);

        GeoPointDto location = row.latitude() != null && row.longitude() != null
                ? new GeoPointDto(row.latitude(), row.longitude())
                : null;
        AddressDto address = new AddressDto(
                row.addressLine1(), row.addressLine2(), row.addressPostalCode(), row.addressCity(),
                row.addressRegionCode(), row.addressCountry(), location);

        List<ImageRefDto> images = uploadAssetLinker.findByRequestId(row.id()).stream()
                // Sem módulo de storage/uploads implementado ainda, não há assinatura real de
                // URL (CLAUDE.md §4) — placeholder explícito, ver relatório de entrega.
                .map(img -> new ImageRefDto(img.imageAssetId(), "https://storage.servimatch.pt/" + img.objectKey(), img.contentType()))
                .toList();

        return new ServiceRequestDto(
                row.id(), row.customerId(), category, row.title(), row.description(), address,
                row.urgency(), row.availability(), row.status(), images,
                // proposalCount não é computado nesta onda (ver relatório de entrega).
                null,
                row.createdAt(), row.publishedAt());
    }
}
