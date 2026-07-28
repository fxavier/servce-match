package pt.servimatch.modules.uploads.internal;

import java.time.Instant;
import java.util.UUID;

/** Espelho de uma linha {@code upload_asset} (V6). */
record UploadAssetRow(
        UUID id,
        UUID ownerUserId,
        String purpose,
        String objectKey,
        String contentType,
        long contentLength,
        String status,
        Instant expiresAt
) {
}
