package pt.servimatch.modules.notifications.internal;

import java.time.Instant;
import java.util.UUID;

record DeviceTokenRow(
        UUID id,
        UUID userId,
        String token,
        String platform,
        String appVersion,
        Instant lastSeenAt,
        Instant createdAt) {
}
