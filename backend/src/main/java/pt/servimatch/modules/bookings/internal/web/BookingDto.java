package pt.servimatch.modules.bookings.internal.web;

import java.time.Instant;
import java.util.UUID;

public record BookingDto(
        UUID id,
        UUID proposalId,
        Instant scheduledStart,
        Instant scheduledEnd,
        String status,
        Instant completedAt) {
}
