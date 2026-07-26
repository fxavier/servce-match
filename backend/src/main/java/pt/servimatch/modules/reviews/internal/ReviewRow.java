package pt.servimatch.modules.reviews.internal;

import java.time.Instant;
import java.util.UUID;

record ReviewRow(UUID id, UUID bookingId, UUID authorId, UUID targetId, int rating, String comment, Instant createdAt) {
}
