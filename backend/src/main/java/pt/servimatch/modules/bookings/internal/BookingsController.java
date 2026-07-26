package pt.servimatch.modules.bookings.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.bookings.internal.web.BookingDto;
import pt.servimatch.modules.users.UsersApi;

import java.util.UUID;

/**
 * Controlador REST de {@code bookings} — ver {@code docs/api/openapi.yaml} (tag Bookings & Reviews).
 *
 * <p>{@code @Lazy}: ver nota em {@code pt.servimatch.modules.requests.internal.RequestsController}.
 */
@RestController
@Lazy
class BookingsController {

    private final BookingsService bookingsService;
    private final UsersApi usersApi;

    BookingsController(BookingsService bookingsService, UsersApi usersApi) {
        this.bookingsService = bookingsService;
        this.usersApi = usersApi;
    }

    @PostMapping("/v1/bookings/{bookingId}/complete")
    BookingDto completeBooking(Authentication authentication, @PathVariable UUID bookingId) {
        UUID requesterId = usersApi.ensureProvisioned(((JwtAuthenticationToken) authentication).getToken());
        return bookingsService.complete(bookingId, requesterId);
    }
}
