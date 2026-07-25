package pt.servimatch.platform.idempotency;

import java.io.Serializable;

/**
 * Resposta armazenada para uma {@code Idempotency-Key}, para repetição
 * exata em caso de retry, e o hash do corpo do pedido original, para
 * detetar reutilização da chave com um payload diferente.
 */
public record CachedIdempotentResponse(
        int status,
        String contentType,
        byte[] body,
        String requestBodyHash
) implements Serializable {
}
