package pt.servimatch.modules.uploads.internal.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Espelha {@code docs/api/openapi.yaml#/components/schemas/UploadTarget}. */
public record UploadTargetResponse(
        UUID imageId,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        Instant expiresAt,
        long maxSizeBytes
) {
    public UploadTargetResponse(UUID imageId, String uploadUrl, Map<String, String> headers, Instant expiresAt, long maxSizeBytes) {
        this(imageId, uploadUrl, "PUT", headers, expiresAt, maxSizeBytes);
    }
}
