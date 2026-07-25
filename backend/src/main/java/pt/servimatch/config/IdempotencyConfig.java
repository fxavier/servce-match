package pt.servimatch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import pt.servimatch.platform.idempotency.IdempotencyProperties;
import pt.servimatch.platform.idempotency.IdempotencyStore;
import pt.servimatch.platform.idempotency.InMemoryIdempotencyStore;
import pt.servimatch.platform.idempotency.RedisIdempotencyStore;

/**
 * Fio condutor do armazenamento de respostas idempotentes (ADR-0006): em
 * memória por omissão (single-instance), Redis quando
 * {@code servimatch.idempotency.backend=redis} (multi-instância).
 */
@Configuration
public class IdempotencyConfig {

    @Bean
    @ConditionalOnProperty(prefix = "servimatch.idempotency", name = "backend", havingValue = "in-memory", matchIfMissing = true)
    public IdempotencyStore inMemoryIdempotencyStore(IdempotencyProperties properties) {
        return new InMemoryIdempotencyStore(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "servimatch.idempotency", name = "backend", havingValue = "redis")
    public IdempotencyStore redisIdempotencyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, IdempotencyProperties properties) {
        return new RedisIdempotencyStore(redisTemplate, objectMapper, properties);
    }
}
