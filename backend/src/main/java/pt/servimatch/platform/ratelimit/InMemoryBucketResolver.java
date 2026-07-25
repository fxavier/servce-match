package pt.servimatch.platform.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;

import java.time.Duration;

/**
 * Buckets em memória do processo (single-instance, ADR-0006). Cada chave
 * fica associada a um {@link Bucket} próprio; entradas inativas são
 * removidas ao fim de um período de inatividade para não crescer sem
 * limite (ex.: um IP que nunca mais volta).
 */
public class InMemoryBucketResolver implements BucketResolver {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(500_000)
            .build();

    @Override
    public Bucket resolve(String key, long capacity, Duration period) {
        return buckets.get(key, k -> Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                .build());
    }
}
