package pt.servimatch.modules.search.internal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cursor opaco para {@code GET /v1/search/providers} — codifica apenas o
 * deslocamento (offset) na ordenação determinística da consulta (ver
 * {@link ProviderSearchRepository}). Escolha deliberada de simplicidade
 * ("mínimo que implementa o contrato"): paginação por offset é O(N) e não
 * por *keyset*, mas é suficiente para o volume do MVP e evita serializar um
 * cursor composto (boost + relevância textual + rating + id) cuja
 * comparação por tuplo seria bem mais frágil de implementar e testar
 * corretamente à primeira. Revisitar com paginação por *keyset* se a
 * profundidade de paginação se tornar um problema de desempenho real.
 */
final class SearchCursor {

    private static final String PREFIX = "off:";

    private SearchCursor() {
    }

    static String encode(int nextOffset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((PREFIX + nextOffset).getBytes(StandardCharsets.UTF_8));
    }

    /** @throws InvalidSearchParametersException se o cursor for ilegível ou negativo. */
    static int decode(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!decoded.startsWith(PREFIX)) {
                throw new IllegalArgumentException("prefixo inesperado");
            }
            int offset = Integer.parseInt(decoded.substring(PREFIX.length()));
            if (offset < 0) {
                throw new IllegalArgumentException("offset negativo");
            }
            return offset;
        } catch (RuntimeException ex) {
            throw new InvalidSearchParametersException("cursor", "Cursor inválido ou corrompido.");
        }
    }
}
