package pt.servimatch.platform.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

/**
 * Armazenamento de respostas idempotentes em Redis, para multi-instância
 * (ADR-0006). Serializa {@link CachedIdempotentResponse} como JSON e usa a
 * expiração nativa do Redis para a janela de retenção.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyStore.class);
    private static final String KEY_PREFIX = "servimatch:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final IdempotencyProperties properties;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, IdempotencyProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<CachedIdempotentResponse> find(String key) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, CachedIdempotentResponse.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize cached idempotent response, treating as cache miss", e);
            return Optional.empty();
        }
    }

    @Override
    public void save(String key, CachedIdempotentResponse response) {
        try {
            String value = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(KEY_PREFIX + key, value, properties.retention());
        } catch (Exception e) {
            log.warn("Failed to serialize idempotent response for caching; the retry will not be short-circuited", e);
        }
    }
}
