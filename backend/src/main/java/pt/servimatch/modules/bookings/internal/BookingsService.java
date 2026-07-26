package pt.servimatch.modules.bookings.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.bookings.BookingStatus;
import pt.servimatch.modules.bookings.BookingsApi;
import pt.servimatch.modules.bookings.internal.web.BookingDto;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.proposals.ProposalsApi;
import pt.servimatch.modules.requests.RequestsApi;

import java.util.Optional;
import java.util.UUID;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository.
@Service
@Lazy
class BookingsService implements BookingsApi {

    private final BookingRepository repository;
    private final ProposalsApi proposalsApi;
    private final RequestsApi requestsApi;
    private final ProvidersApi providersApi;

    BookingsService(BookingRepository repository, ProposalsApi proposalsApi, RequestsApi requestsApi, ProvidersApi providersApi) {
        this.repository = repository;
        this.proposalsApi = proposalsApi;
        this.requestsApi = requestsApi;
        this.providersApi = providersApi;
    }

    @Override
    @Transactional
    public UUID createFromProposal(UUID proposalId) {
        return repository.insertIfAbsent(proposalId);
    }

    @Override
    public Optional<BookingView> findById(UUID bookingId) {
        return repository.findById(bookingId).map(this::toView);
    }

    @Override
    public Optional<BookingParticipants> participants(UUID bookingId) {
        return repository.findById(bookingId).flatMap(booking -> resolveParticipants(booking.proposalId())
                .map(p -> new BookingParticipants(bookingId, BookingStatus.valueOf(booking.status()), p.customerId(), p.providerUserId())));
    }

    /** Usado pelo controller deste módulo (completeBooking). */
    @Transactional
    BookingDto complete(UUID bookingId, UUID requesterUserId) {
        BookingRow booking = repository.findById(bookingId)
                .orElseThrow(() -> Problems.notFound("Marcação não encontrada."));
        ParticipantIds ids = resolveParticipants(booking.proposalId())
                .orElseThrow(() -> Problems.notFound("Não foi possível resolver os participantes da marcação."));
        if (!requesterUserId.equals(ids.customerId()) && !requesterUserId.equals(ids.providerUserId())) {
            throw Problems.forbidden("Só um participante da marcação a pode concluir.");
        }
        if (!repository.complete(bookingId)) {
            throw Problems.conflict("Marcação não está num estado que permita concluir (status atual: " + booking.status() + ").");
        }
        return toDto(repository.findById(bookingId).orElseThrow());
    }

    private record ParticipantIds(UUID customerId, UUID providerUserId) {
    }

    private Optional<ParticipantIds> resolveParticipants(UUID proposalId) {
        return proposalsApi.findById(proposalId).flatMap(proposal ->
                requestsApi.get(proposal.requestId()).flatMap(request ->
                        providersApi.findUserIdByProviderId(proposal.providerId())
                                .map(providerUserId -> new ParticipantIds(request.customerId(), providerUserId))));
    }

    private BookingView toView(BookingRow row) {
        return new BookingView(row.id(), row.proposalId(), BookingStatus.valueOf(row.status()), row.completedAt());
    }

    private BookingDto toDto(BookingRow row) {
        return new BookingDto(row.id(), row.proposalId(), row.scheduledStart(), row.scheduledEnd(), row.status(), row.completedAt());
    }
}
