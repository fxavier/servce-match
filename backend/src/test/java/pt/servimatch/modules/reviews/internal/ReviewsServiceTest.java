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
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.reviews.internal.web.CreateReviewRequest;
import pt.servimatch.modules.reviews.internal.web.ReviewDto;
import pt.servimatch.modules.reviews.internal.web.ReviewWithAuthorPageDto;
import pt.servimatch.modules.users.UsersApi;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    @Mock
    private ProvidersApi providersApi;
    @Mock
    private UsersApi usersApi;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID providerUserId = UUID.randomUUID();

    private ReviewsService service() {
        return new ReviewsService(repository, bookingsApi, providersApi, usersApi);
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

    /**
     * {@code listProviderReviews}: público, PII reduzida no servidor
     * ({@code authorName} nunca é o {@code display_name} completo) e
     * resolução em lote — um único {@code UsersApi#findByIds} para a
     * página, nunca um {@code findById} por avaliação.
     */
    @Test
    void listForProviderReducesAuthorNameAndResolvesInBatch() {
        UUID providerId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        when(providersApi.checkEligibility(providerId))
                .thenReturn(Optional.of(new ProvidersApi.ProviderEligibility(providerId, true, true)));
        when(providersApi.findUserIdByProviderId(providerId)).thenReturn(Optional.of(providerUserId));
        when(repository.findPageByTarget(providerUserId, null, 21)).thenReturn(List.of(
                new ReviewWithAuthorRow(reviewId, customerId, providerUserId, 5, "Excelente", Instant.now(), null)));
        when(usersApi.findByIds(Set.of(customerId)))
                .thenReturn(Map.of(customerId, new UsersApi.UserSummaryView(customerId, "Mariana Costa")));

        ReviewWithAuthorPageDto result = service().listForProvider(providerId, null, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).authorName()).isEqualTo("Mariana C.");
        assertThat(result.items().get(0).authorName()).doesNotContain("Costa");
        assertThat(result.items().get(0).authorAvatarSeed()).isEqualTo(customerId.toString());
    }

    @Test
    void listForProviderReturnsNotFoundWhenProviderIsNotVisible() {
        UUID providerId = UUID.randomUUID();
        when(providersApi.checkEligibility(providerId))
                .thenReturn(Optional.of(new ProvidersApi.ProviderEligibility(providerId, false, true)));

        assertThatThrownBy(() -> service().listForProvider(providerId, null, 20))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listForProviderReturnsNotFoundWhenProviderDoesNotExist() {
        UUID providerId = UUID.randomUUID();
        when(providersApi.checkEligibility(providerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().listForProvider(providerId, null, 20))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
