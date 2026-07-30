package pt.servimatch.modules.reviews.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.reviews.internal.web.ReviewWithAuthorPageDto;

import java.util.UUID;

/**
 * {@code GET /v1/providers/{providerId}/reviews} (operationId
 * {@code listProviderReviews}) — público (`security: []` no contrato,
 * {@code SecurityConfig.PUBLIC_GET_ENDPOINTS} já inclui
 * {@code /v1/providers/*}/{@code reviews}, não é preciso alterar
 * segurança). Controlador separado de {@link ReviewsController} (que serve
 * {@code /v1/reviews}, autenticado) para não misturar as duas superfícies
 * — mesmo padrão de {@code SearchController} vs. o resto de
 * {@code providers}/{@code search}.
 *
 * <p>{@code @Lazy}: ver nota em
 * {@code pt.servimatch.modules.requests.internal.RequestsController}.
 */
@RestController
@Lazy
class ProviderReviewsController {

    private final ReviewsService reviewsService;

    ProviderReviewsController(ReviewsService reviewsService) {
        this.reviewsService = reviewsService;
    }

    @GetMapping("/v1/providers/{providerId}/reviews")
    ReviewWithAuthorPageDto listProviderReviews(@PathVariable UUID providerId,
                                                 @RequestParam(required = false) String cursor,
                                                 @RequestParam(required = false, defaultValue = "20") int limit) {
        return reviewsService.listForProvider(providerId, cursor, Math.min(Math.max(limit, 1), 100));
    }
}
