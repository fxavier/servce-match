package pt.servimatch.modules.bookings.internal;

import java.time.Instant;
import java.util.UUID;

record BookingRow(
        UUID id,
        UUID proposalId,
        Instant scheduledStart,
        Instant scheduledEnd,
        String status,
        Instant completedAt,
        Instant createdAt) {
}
