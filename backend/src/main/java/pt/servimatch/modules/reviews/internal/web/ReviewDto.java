package pt.servimatch.modules.reviews.internal.web;

import java.time.Instant;
import java.util.UUID;

public record ReviewDto(UUID id, UUID bookingId, UUID authorId, UUID targetId, int rating, String comment, Instant createdAt) {
}
