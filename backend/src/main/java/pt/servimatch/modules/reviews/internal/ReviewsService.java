package pt.servimatch.modules.reviews.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.bookings.BookingStatus;
import pt.servimatch.modules.bookings.BookingsApi;
import pt.servimatch.modules.reviews.internal.web.CreateReviewRequest;
import pt.servimatch.modules.reviews.internal.web.ReviewDto;

import java.util.UUID;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository.
@Service
@Lazy
class ReviewsService {

    private final ReviewRepository repository;
    private final BookingsApi bookingsApi;

    ReviewsService(ReviewRepository repository, BookingsApi bookingsApi) {
        this.repository = repository;
        this.bookingsApi = bookingsApi;
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
}
