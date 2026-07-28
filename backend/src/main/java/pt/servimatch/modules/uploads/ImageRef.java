package pt.servimatch.modules.uploads;

import java.util.UUID;

/**
 * Referência resolvida a um ficheiro já confirmado, pronta a expor num DTO
 * de outro módulo (espelha
 * {@code docs/api/openapi.yaml#/components/schemas/ImageRef}).
 *
 * <p>{@code url} é <b>sempre</b> um URL assinado com expiração (CLAUDE.md
 * §4) — nunca o caminho do objeto em bruto. Gerado de novo em cada
 * {@link UploadsApi#resolve}, porque o URL anterior pode já ter expirado; os
 * módulos de domínio não devem persistir nem colocar em cache este valor.
 */
public record ImageRef(UUID id, String url, String contentType) {
}
