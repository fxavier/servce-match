package pt.servimatch.modules.reviews.internal;

import java.time.Instant;
import java.util.UUID;

/** Linha de {@code listProviderReviews} — inclui {@code provider_response}/{@code provider_response_at} (V17). */
record ReviewWithAuthorRow(
        UUID id,
        UUID authorId,
        UUID targetId,
        int rating,
        String comment,
        Instant createdAt,
        String providerResponse) {
}
