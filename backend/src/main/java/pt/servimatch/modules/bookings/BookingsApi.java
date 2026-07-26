package pt.servimatch.modules.bookings;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** API pública do módulo {@code bookings}, usada por {@code proposals} e {@code reviews}. */
public interface BookingsApi {

    /** Cria a marcação (estado inicial {@code CONFIRMED}) a partir de uma proposta aceite. */
    UUID createFromProposal(UUID proposalId);

    Optional<BookingView> findById(UUID bookingId);

    /**
     * Resolve os dois participantes da marcação (cliente e utilizador por
     * trás do prestador) compondo {@code proposals}, {@code requests} e
     * {@code providers} — usado para autorização (ownership) por
     * {@code completeBooking} e pelo módulo {@code reviews}.
     */
    Optional<BookingParticipants> participants(UUID bookingId);

    record BookingView(UUID id, UUID proposalId, BookingStatus status, Instant completedAt) {
    }

    record BookingParticipants(UUID bookingId, BookingStatus status, UUID customerId, UUID providerUserId) {
    }
}
