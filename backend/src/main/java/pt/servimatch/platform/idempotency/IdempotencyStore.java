package pt.servimatch.platform.idempotency;

import java.util.Optional;

/**
 * Armazenamento de respostas idempotentes. Implementações: em memória
 * (single-instance) ou Redis (multi-instância, ADR-0006) — a escolha é por
 * configuração ({@code servimatch.idempotency.backend}), nunca por
 * ramificação no código que a usa (ver {@link IdempotencyFilter}).
 */
public interface IdempotencyStore {

    Optional<CachedIdempotentResponse> find(String key);

    void save(String key, CachedIdempotentResponse response);
}
