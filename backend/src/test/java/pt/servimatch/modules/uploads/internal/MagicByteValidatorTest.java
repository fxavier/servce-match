package pt.servimatch.modules.uploads.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobertura rápida (sem I/O) das assinaturas suportadas — a verificação
 * real contra o objeto em armazenamento é coberta por
 * {@code UploadsApiIntegrationTest} (Testcontainers/MinIO), que é onde a
 * garantia do CLAUDE.md §4 ("validar por magic bytes, nunca por extensão")
 * é realmente provada ponta-a-ponta.
 */
class MagicByteValidatorTest {

    @Test
    void jpegHeaderMatchesDeclaredJpeg() {
        byte[] header = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(MagicByteValidator.matches(header, "image/jpeg")).isTrue();
    }

    @Test
    void pngHeaderMatchesDeclaredPng() {
        byte[] header = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(MagicByteValidator.matches(header, "image/png")).isTrue();
    }

    @Test
    void webpHeaderMatchesDeclaredWebp() {
        byte[] header = new byte[16];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, header, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, header, 8, 4);
        assertThat(MagicByteValidator.matches(header, "image/webp")).isTrue();
    }

    @Test
    void pdfHeaderMatchesDeclaredPdf() {
        byte[] header = new byte[16];
        System.arraycopy("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 0, header, 0, 8);
        assertThat(MagicByteValidator.matches(header, "application/pdf")).isTrue();
    }

    @Test
    void plainTextRejectedWhenDeclaredAsJpeg() {
        byte[] header = "não sou uma imagem".getBytes(StandardCharsets.UTF_8);
        assertThat(MagicByteValidator.matches(header, "image/jpeg")).isFalse();
    }

    @Test
    void pngBytesRejectedWhenDeclaredAsJpeg() {
        byte[] header = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(MagicByteValidator.matches(header, "image/jpeg")).isFalse();
    }

    @Test
    void unknownDeclaredContentTypeNeverMatches() {
        byte[] header = bytes(0xFF, 0xD8, 0xFF);
        assertThat(MagicByteValidator.matches(header, "application/x-msdownload")).isFalse();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
