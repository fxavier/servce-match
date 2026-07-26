package pt.servimatch.modules.reviews.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.modules.bookings.BookingStatus;
import pt.servimatch.modules.bookings.BookingsApi;
import pt.servimatch.modules.reviews.internal.web.CreateReviewRequest;
import pt.servimatch.modules.reviews.internal.web.ReviewDto;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Avaliação verificada (CLAUDE.md — "não existe review sem prestação
 * concluída"): só um {@code Booking COMPLETED} habilita avaliação, só entre
 * os dois participantes, e a duplicação é rejeitada.
 */
@ExtendWith(MockitoExtension.class)
class ReviewsServiceTest {

    @Mock
    private ReviewRepository repository;
    @Mock
    private BookingsApi bookingsApi;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID providerUserId = UUID.randomUUID();

    private ReviewsService service() {
        return new ReviewsService(repository, bookingsApi);
    }

    @Test
    void createHappyPathByCustomerReviewingProvider() {
        when(bookingsApi.participants(bookingId)).thenReturn(Optional.of(
                new BookingsApi.BookingParticipants(bookingId, BookingStatus.COMPLETED, customerId, providerUserId)));
        when(repository.insert(bookingId, customerId, providerUserId, 5, "Excelente"))
                .thenReturn(new ReviewRow(UUID.randomUUID(), bookingId, customerId, providerUserId, 5, "Excelente", Instant.now()));

        CreateReviewRequest request = new CreateReviewRequest(bookingId, providerUserId, 5, "Excelente");
        ReviewDto result = service().create(customerId, request);

        assertThat(result.targetId()).isEqualTo(providerUserId);
        assertThat(result.rating()).isEqualTo(5);
    }

    @Test
    void createRejectsWhenBookingNotCompleted() {
        when(bookingsApi.participants(bookingId)).thenReturn(Optional.of(
                new BookingsApi.BookingParticipants(bookingId, BookingStatus.CONFIRMED, customerId, providerUserId)));

        CreateReviewRequest request = new CreateReviewRequest(bookingId, providerUserId, 5, null);

        assertThatThrownBy(() -> service().create(customerId, request))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void createRejectsNonParticipantAuthor() {
        when(bookingsApi.participants(bookingId)).thenReturn(Optional.of(
                new BookingsApi.BookingParticipants(bookingId, BookingStatus.COMPLETED, customerId, providerUserId)));

        UUID stranger = UUID.randomUUID();
        CreateReviewRequest request = new CreateReviewRequest(bookingId, providerUserId, 5, null);

        assertThatThrownBy(() -> service().create(stranger, request))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createRejectsDuplicateReviewFromSameAuthor() {
        when(bookingsApi.participants(bookingId)).thenReturn(Optional.of(
                new BookingsApi.BookingParticipants(bookingId, BookingStatus.COMPLETED, customerId, providerUserId)));
        when(repository.insert(bookingId, customerId, providerUserId, 4, null))
                .thenThrow(new DataIntegrityViolationException("uq_review_booking_author"));

        CreateReviewRequest request = new CreateReviewRequest(bookingId, providerUserId, 4, null);

        assertThatThrownBy(() -> service().create(customerId, request))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
