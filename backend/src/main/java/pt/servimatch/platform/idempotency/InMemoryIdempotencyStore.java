package pt.servimatch.platform.idempotency;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Optional;

/**
 * Armazenamento de respostas idempotentes em memória do processo, com TTL
 * igual à janela de retenção configurada. Adequado a single-instance
 * (ADR-0006); não sobrevive a reinício nem é partilhado entre instâncias.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Cache<String, CachedIdempotentResponse> cache;

    public InMemoryIdempotencyStore(IdempotencyProperties properties) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.retention())
                .maximumSize(100_000)
                .build();
    }

    @Override
    public Optional<CachedIdempotentResponse> find(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    @Override
    public void save(String key, CachedIdempotentResponse response) {
        cache.put(key, response);
    }
}
