package pt.servimatch.modules.requests.internal.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceRequestDto(
        UUID id,
        UUID customerId,
        CategoryDto category,
        String title,
        String description,
        AddressDto address,
        String urgency,
        String availability,
        String status,
        List<ImageRefDto> images,
        Integer proposalCount,
        Instant createdAt,
        Instant publishedAt) {
}
