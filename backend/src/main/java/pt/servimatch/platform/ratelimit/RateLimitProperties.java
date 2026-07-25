package pt.servimatch.platform.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração do rate limiting transversal (Bucket4j, ADR-0006).
 *
 * <p>Este filtro aplica um limite <b>por defeito</b>, por cliente (IP, ou
 * utilizador autenticado quando disponível), a todos os pedidos — uma rede
 * de segurança contra abuso. Endpoints particularmente sensíveis (criação
 * de pedidos/propostas, upload, login-adjacent) podem pedir um limite mais
 * apertado ao agente {@code backend-platform}, que o expõe através de
 * {@link RateLimiterService} para uso pontual num módulo de domínio.
 *
 * @param enabled      liga/desliga o filtro global (nunca desligar em produção).
 * @param capacity     nº máximo de pedidos na janela de refil.
 * @param refillPeriod duração da janela de refil (refil "greedy": distribuído ao longo da janela).
 * @param backend      {@code in-memory} (omissão, single-instance) ou {@code redis} (multi-instância).
 */
@ConfigurationProperties(prefix = "servimatch.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        long capacity,
        Duration refillPeriod,
        String backend
) {
    public RateLimitProperties {
        if (capacity <= 0) {
            capacity = 120;
        }
        if (refillPeriod == null) {
            refillPeriod = Duration.ofMinutes(1);
        }
        if (backend == null || backend.isBlank()) {
            backend = "in-memory";
        }
    }
}
