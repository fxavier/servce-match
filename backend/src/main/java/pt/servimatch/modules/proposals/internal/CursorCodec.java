package pt.servimatch.modules.proposals.internal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Ver {@code pt.servimatch.modules.requests.internal.CursorCodec} para a nota de design. */
final class CursorCodec {

    private CursorCodec() {
    }

    record Position(Instant createdAt, UUID id) {
    }

    static String encode(Instant createdAt, UUID id) {
        String raw = createdAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Optional<Position> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.indexOf(':');
            Instant createdAt = Instant.ofEpochMilli(Long.parseLong(raw.substring(0, sep)));
            UUID id = UUID.fromString(raw.substring(sep + 1));
            return Optional.of(new Position(createdAt, id));
        } catch (RuntimeException e) {
            throw Problems.unprocessable("Cursor inválido.");
        }
    }
}
