package pt.servimatch.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.servimatch.platform.ratelimit.BucketResolver;
import pt.servimatch.platform.ratelimit.InMemoryBucketResolver;
import pt.servimatch.platform.ratelimit.RateLimiterService;
import pt.servimatch.platform.ratelimit.RedisBucketResolver;

import java.time.Duration;

/**
 * Fio condutor do rate limiting Bucket4j (ADR-0006): backend em memória por
 * omissão (single-instance), Redis quando
 * {@code servimatch.rate-limit.backend=redis} (multi-instância). A escolha
 * é inteiramente por configuração — nenhum consumidor de
 * {@link BucketResolver} sabe qual dos dois está ativo.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(prefix = "servimatch.rate-limit", name = "backend", havingValue = "in-memory", matchIfMissing = true)
    public BucketResolver inMemoryBucketResolver() {
        return new InMemoryBucketResolver();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "servimatch.rate-limit", name = "backend", havingValue = "redis")
    public RedisClient bucket4jRedisClient(RedisProperties redisProperties) {
        RedisURI.Builder uriBuilder = RedisURI.Builder.redis(redisProperties.getHost(), redisProperties.getPort());
        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
            uriBuilder.withPassword((CharSequence) redisProperties.getPassword());
        }
        return RedisClient.create(uriBuilder.build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "servimatch.rate-limit", name = "backend", havingValue = "redis")
    public ProxyManager<byte[]> bucket4jProxyManager(RedisClient redisClient) {
        return Bucket4jLettuce.casBasedBuilder(redisClient)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "servimatch.rate-limit", name = "backend", havingValue = "redis")
    public BucketResolver redisBucketResolver(ProxyManager<byte[]> proxyManager) {
        return new RedisBucketResolver(proxyManager);
    }

    /** Exposto para módulos de domínio aplicarem limites por endpoint (ver {@link RateLimiterService}). */
    @Bean
    public RateLimiterService rateLimiterService(BucketResolver bucketResolver) {
        return new RateLimiterService(bucketResolver);
    }
}
