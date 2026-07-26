package pt.servimatch.modules.requests.internal.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateServiceRequestRequest(
        @NotNull UUID categoryId,
        @NotNull @Size(min = 3, max = 140) String title,
        @Size(max = 4000) String description,
        @NotNull @Valid AddressDto address,
        String urgency,
        String availability,
        List<UUID> imageIds) {
}
