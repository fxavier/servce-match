package pt.servimatch.modules.reviews.internal;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import pt.servimatch.modules.reviews.internal.web.CreateReviewRequest;
import pt.servimatch.modules.reviews.internal.web.ReviewDto;
import pt.servimatch.modules.users.UsersApi;

import java.net.URI;
import java.util.UUID;

/**
 * Controlador REST de {@code reviews} — ver {@code docs/api/openapi.yaml} (tag Bookings & Reviews).
 *
 * <p>{@code @Lazy}: ver nota em {@code pt.servimatch.modules.requests.internal.RequestsController}.
 */
@RestController
@Lazy
class ReviewsController {

    private final ReviewsService reviewsService;
    private final UsersApi usersApi;

    ReviewsController(ReviewsService reviewsService, UsersApi usersApi) {
        this.reviewsService = reviewsService;
        this.usersApi = usersApi;
    }

    @PostMapping("/v1/reviews")
    ResponseEntity<ReviewDto> createReview(Authentication authentication, @Valid @RequestBody CreateReviewRequest request,
                                            UriComponentsBuilder uriBuilder) {
        UUID authorId = usersApi.ensureProvisioned(((JwtAuthenticationToken) authentication).getToken());
        ReviewDto created = reviewsService.create(authorId, request);
        URI location = uriBuilder.path("/v1/reviews/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }
}
