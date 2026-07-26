package pt.servimatch.modules.bookings.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.modules.bookings.internal.web.BookingDto;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.proposals.ProposalStatus;
import pt.servimatch.modules.proposals.ProposalsApi;
import pt.servimatch.modules.requests.RequestsApi;
import pt.servimatch.modules.requests.ServiceRequestStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@code completeBooking}: ownership (só um participante conclui) e a
 * transição de estado (CONFIRMED/IN_PROGRESS → COMPLETED, ARQUITETURA §13).
 */
@ExtendWith(MockitoExtension.class)
class BookingsServiceTest {

    @Mock
    private BookingRepository repository;
    @Mock
    private ProposalsApi proposalsApi;
    @Mock
    private RequestsApi requestsApi;
    @Mock
    private ProvidersApi providersApi;

    private BookingsService service;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID proposalId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID providerUserId = UUID.randomUUID();

    private BookingsService newService() {
        return new BookingsService(repository, proposalsApi, requestsApi, providersApi);
    }

    private void stubParticipants() {
        when(proposalsApi.findById(proposalId))
                .thenReturn(Optional.of(new ProposalsApi.ProposalView(proposalId, requestId, providerId, ProposalStatus.ACCEPTED)));
        when(requestsApi.get(requestId))
                .thenReturn(Optional.of(new RequestsApi.RequestSummary(requestId, customerId, UUID.randomUUID(), ServiceRequestStatus.CONFIRMED)));
        when(providersApi.findUserIdByProviderId(providerId)).thenReturn(Optional.of(providerUserId));
    }

    @Test
    void completeHappyPathByCustomer() {
        service = newService();
        BookingRow booking = new BookingRow(bookingId, proposalId, null, null, "CONFIRMED", null, Instant.now());
        when(repository.findById(bookingId))
                .thenReturn(Optional.of(booking))
                .thenReturn(Optional.of(new BookingRow(bookingId, proposalId, null, null, "COMPLETED", Instant.now(), Instant.now())));
        stubParticipants();
        when(repository.complete(bookingId)).thenReturn(true);

        BookingDto result = service.complete(bookingId, customerId);

        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    void completeRejectsNonParticipant() {
        service = newService();
        BookingRow booking = new BookingRow(bookingId, proposalId, null, null, "CONFIRMED", null, Instant.now());
        when(repository.findById(bookingId)).thenReturn(Optional.of(booking));
        stubParticipants();

        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> service.complete(bookingId, stranger))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void completeRejectsAlreadyCompletedBooking() {
        service = newService();
        BookingRow booking = new BookingRow(bookingId, proposalId, null, null, "COMPLETED", Instant.now(), Instant.now());
        when(repository.findById(bookingId)).thenReturn(Optional.of(booking));
        stubParticipants();
        when(repository.complete(bookingId)).thenReturn(false);

        assertThatThrownBy(() -> service.complete(bookingId, customerId))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
