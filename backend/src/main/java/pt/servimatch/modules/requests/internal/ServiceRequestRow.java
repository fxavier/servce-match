package pt.servimatch.modules.requests.internal;

import java.time.Instant;
import java.util.UUID;

record ServiceRequestRow(
        UUID id,
        UUID customerId,
        UUID categoryId,
        String title,
        String description,
        String addressLine1,
        String addressLine2,
        String addressPostalCode,
        String addressCity,
        String addressRegionCode,
        String addressCountry,
        Double latitude,
        Double longitude,
        String urgency,
        String availability,
        String status,
        Instant publishedAt,
        Instant createdAt) {
}
