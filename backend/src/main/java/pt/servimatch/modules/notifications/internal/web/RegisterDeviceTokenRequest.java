package pt.servimatch.modules.notifications.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pt.servimatch.modules.notifications.internal.DevicePlatform;

/** Espelha {@code docs/api/openapi.yaml#/components/schemas/RegisterDeviceToken}. */
public record RegisterDeviceTokenRequest(
        @NotBlank @Size(max = 512) String token,
        @NotNull DevicePlatform platform,
        @Size(max = 30) String appVersion
) {
}
