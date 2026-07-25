package pt.servimatch.platform.ratelimit;

import io.github.bucket4j.ConsumptionProbe;

import java.time.Duration;

/**
 * Ponto de extensão para módulos de domínio aplicarem um limite mais
 * apertado a um endpoint sensível específico (ex.: criação de pedidos,
 * envio de propostas, upload), além do limite global já aplicado por
 * {@link RateLimitFilter}. Usa o mesmo {@link BucketResolver} — em memória
 * ou Redis conforme {@code servimatch.rate-limit.backend} (ADR-0006) — pelo
 * que o comportamento em multi-instância é consistente com o limite
 * global.
 *
 * <p>Exemplo de uso num serviço de domínio:
 * <pre>{@code
 * if (!rateLimiterService.tryConsume("create-request:" + customerId, 10, Duration.ofMinutes(1))) {
 *     throw new RateLimitExceededException();
 * }
 * }</pre>
 */
public class RateLimiterService {

    private final BucketResolver bucketResolver;

    public RateLimiterService(BucketResolver bucketResolver) {
        this.bucketResolver = bucketResolver;
    }

    /** @return {@code true} se o pedido foi admitido, {@code false} se o limite foi excedido. */
    public boolean tryConsume(String key, long capacity, Duration period) {
        return bucketResolver.resolve(key, capacity, period).tryConsume(1);
    }

    public ConsumptionProbe consumeAndProbe(String key, long capacity, Duration period) {
        return bucketResolver.resolve(key, capacity, period).tryConsumeAndReturnRemaining(1);
    }
}
