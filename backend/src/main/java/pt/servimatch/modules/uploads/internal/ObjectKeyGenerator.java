package pt.servimatch.modules.uploads.internal;

import org.springframework.stereotype.Component;
import pt.servimatch.modules.uploads.UploadPurpose;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/**
 * Gera a chave de armazenamento no servidor — nunca a partir do
 * {@code fileName} do cliente (CLAUDE.md §4: evita <em>path traversal</em>
 * e colisão de nomes). O {@code fileName} original é puramente informativo
 * e não é usado aqui nem persistido.
 */
@Component
class ObjectKeyGenerator {

    private final Clock clock;

    ObjectKeyGenerator() {
        this(Clock.systemUTC());
    }

    ObjectKeyGenerator(Clock clock) {
        this.clock = clock;
    }

    String generate(UploadPurpose purpose, String contentType) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return "%s/%04d/%02d/%02d/%s.%s".formatted(
                purpose.name().toLowerCase(Locale.ROOT),
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(),
                UUID.randomUUID(),
                extensionFor(contentType));
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> "bin";
        };
    }
}
