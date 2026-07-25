package pt.servimatch.platform.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Buckets distribuídos via Redis (multi-instância, ADR-0006), usando o
 * {@code ProxyManager} do bucket4j-lettuce. Sem isto, os limites seriam
 * por-instância e contornáveis atrás de um load balancer.
 */
public class RedisBucketResolver implements BucketResolver {

    private final ProxyManager<byte[]> proxyManager;

    public RedisBucketResolver(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    public Bucket resolve(String key, long capacity, Duration period) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                .build();
        return proxyManager.builder().build(key.getBytes(StandardCharsets.UTF_8), () -> configuration);
    }
}
