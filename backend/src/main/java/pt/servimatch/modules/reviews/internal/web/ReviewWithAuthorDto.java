package pt.servimatch.modules.reviews.internal.web;

import java.time.Instant;
import java.util.UUID;

public record ReviewWithAuthorDto(
        UUID id,
        String authorName,
        String authorAvatarSeed,
        UUID targetId,
        int rating,
        String comment,
        Instant createdAt,
        String providerResponse) {
}
