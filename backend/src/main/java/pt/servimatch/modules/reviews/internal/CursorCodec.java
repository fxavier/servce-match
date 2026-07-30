package pt.servimatch.modules.reviews.internal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Cursor opaco para paginação de {@code listProviderReviews} (ARQUITETURA
 * §11.1, CLAUDE.md §5): codifica {@code (createdAt, id)} da última
 * avaliação da página anterior. Ordenação estável por {@code created_at
 * DESC, id DESC} (índice {@code idx_review_target_id_created_at_id}, V17);
 * o cursor é a fronteira exclusiva da página seguinte. Ver
 * {@code pt.servimatch.modules.requests.internal.CursorCodec} para a nota
 * de design (duplicado deliberadamente por módulo, não partilhado).
 */
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
