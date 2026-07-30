package pt.servimatch.modules.reviews.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.bookings.BookingStatus;
import pt.servimatch.modules.bookings.BookingsApi;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.reviews.internal.web.CreateReviewRequest;
import pt.servimatch.modules.reviews.internal.web.PageMetaDto;
import pt.servimatch.modules.reviews.internal.web.ReviewDto;
import pt.servimatch.modules.reviews.internal.web.ReviewWithAuthorDto;
import pt.servimatch.modules.reviews.internal.web.ReviewWithAuthorPageDto;
import pt.servimatch.modules.users.UsersApi;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository.
@Service
@Lazy
class ReviewsService {

    private final ReviewRepository repository;
    private final BookingsApi bookingsApi;
    private final ProvidersApi providersApi;
    private final UsersApi usersApi;

    ReviewsService(ReviewRepository repository, BookingsApi bookingsApi, ProvidersApi providersApi, UsersApi usersApi) {
        this.repository = repository;
        this.bookingsApi = bookingsApi;
        this.providersApi = providersApi;
        this.usersApi = usersApi;
    }

    /**
     * Avaliação verificada (CLAUDE.md): só é possível quando existe um
     * {@code Booking COMPLETED} entre autor e alvo, cada um avalia a mesma
     * marcação no máximo uma vez. A garantia final é do schema (trigger +
     * índice único em {@code review}, V10) — aqui valida-se antes só para
     * devolver uma mensagem de erro melhor.
     */
    @Transactional
    ReviewDto create(UUID authorId, CreateReviewRequest request) {
        BookingsApi.BookingParticipants participants = bookingsApi.participants(request.bookingId())
                .orElseThrow(() -> Problems.notFound("Marcação não encontrada."));

        if (participants.status() != BookingStatus.COMPLETED) {
            throw Problems.conflict("A marcação ainda não foi concluída.");
        }

        UUID otherParty;
        if (authorId.equals(participants.customerId())) {
            otherParty = participants.providerUserId();
        } else if (authorId.equals(participants.providerUserId())) {
            otherParty = participants.customerId();
        } else {
            throw Problems.forbidden("Só um participante da marcação pode avaliar.");
        }
        if (otherParty == null || !otherParty.equals(request.targetId())) {
            throw Problems.forbidden("O alvo da avaliação tem de ser a outra parte da marcação.");
        }

        try {
            ReviewRow row = repository.insert(request.bookingId(), authorId, request.targetId(), request.rating(), request.comment());
            return toDto(row);
        } catch (DataIntegrityViolationException e) {
            throw Problems.conflict("Já avaliou esta marcação.");
        }
    }

    private ReviewDto toDto(ReviewRow row) {
        return new ReviewDto(row.id(), row.bookingId(), row.authorId(), row.targetId(), row.rating(), row.comment(), row.createdAt());
    }

    /**
     * {@code GET /v1/providers/{providerId}/reviews}: público, sem
     * autenticação. {@code 404} se o prestador não existir ou não estiver
     * visível — mesmo predicado ({@code approved && visible},
     * {@link ProvidersApi.ProviderEligibility#isEligible()}) usado por
     * {@code GET /v1/providers/{id}} e {@code GET /v1/search/providers}
     * (contrato, descrição de {@code listProviderReviews}): este endpoint
     * nunca serve de oráculo sobre um prestador escondido ou pendente de
     * aprovação, por isso 404 aqui, não 403 — não há "dono" autenticado a
     * quem negar acesso, a própria existência visível é o que está a ser
     * verificado.
     *
     * <p>{@code authorName} é PII reduzida ({@link AuthorNameReducer}),
     * nunca o {@code display_name} completo — resolvido em lote por página
     * ({@link UsersApi#findByIds}), nunca um {@link UsersApi#findById} por
     * avaliação.
     */
    @Transactional(readOnly = true)
    ReviewWithAuthorPageDto listForProvider(UUID providerId, String cursor, int limit) {
        boolean visible = providersApi.checkEligibility(providerId)
                .map(ProvidersApi.ProviderEligibility::isEligible)
                .orElse(false);
        if (!visible) {
            throw Problems.notFound("Prestador não encontrado.");
        }
        UUID targetUserId = providersApi.findUserIdByProviderId(providerId)
                .orElseThrow(() -> Problems.notFound("Prestador não encontrado."));

        CursorCodec.Position after = CursorCodec.decode(cursor).orElse(null);
        List<ReviewWithAuthorRow> rows = repository.findPageByTarget(targetUserId, after, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ReviewWithAuthorRow> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore
                ? CursorCodec.encode(page.get(page.size() - 1).createdAt(), page.get(page.size() - 1).id())
                : null;

        Set<UUID> authorIds = new HashSet<>();
        for (ReviewWithAuthorRow row : page) {
            authorIds.add(row.authorId());
        }
        Map<UUID, UsersApi.UserSummaryView> authors = usersApi.findByIds(authorIds);

        List<ReviewWithAuthorDto> items = page.stream().map(row -> toDtoWithAuthor(row, authors)).toList();
        return new ReviewWithAuthorPageDto(items, new PageMetaDto(nextCursor));
    }

    private ReviewWithAuthorDto toDtoWithAuthor(ReviewWithAuthorRow row, Map<UUID, UsersApi.UserSummaryView> authors) {
        String fullName = authors.getOrDefault(row.authorId(), new UsersApi.UserSummaryView(row.authorId(), "")).displayName();
        return new ReviewWithAuthorDto(
                row.id(),
                AuthorNameReducer.reduce(fullName),
                row.authorId().toString(),
                row.targetId(),
                row.rating(),
                row.comment(),
                row.createdAt(),
                row.providerResponse());
    }
}
