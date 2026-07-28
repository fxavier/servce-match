package pt.servimatch.modules.uploads.internal;

/**
 * Validação por <em>magic bytes</em> (CLAUDE.md §4: "validar por magic
 * bytes, nunca por extensão"). Compara os primeiros bytes do objeto em
 * armazenamento com a assinatura conhecida do {@code contentType}
 * declarado — nunca confia no {@code Content-Type} que o cliente enviou.
 */
final class MagicByteValidator {

    /** Bytes suficientes para cobrir a maior assinatura suportada (WEBP: offset 8-11). */
    static final int HEADER_BYTES = 16;

    private MagicByteValidator() {
    }

    static boolean matches(byte[] header, String declaredContentType) {
        if (header == null || declaredContentType == null) {
            return false;
        }
        return switch (declaredContentType) {
            case "image/jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> header.length >= 12
                    && startsWith(header, 0x52, 0x49, 0x46, 0x46) // "RIFF"
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            case "application/pdf" -> startsWith(header, 0x25, 0x50, 0x44, 0x46, 0x2D); // "%PDF-"
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
