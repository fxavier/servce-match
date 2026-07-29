package pt.servimatch.platform.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

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
 * @param enabled        liga/desliga o filtro global (nunca desligar em produção).
 * @param capacity       nº máximo de pedidos na janela de refil.
 * @param refillPeriod   duração da janela de refil (refil "greedy": distribuído ao longo da janela).
 * @param backend        {@code in-memory} (omissão, single-instance) ou {@code redis} (multi-instância).
 * @param trustedProxies CIDR (ou IP exato) dos proxies/load balancers de confiança cujo
 *                       {@code X-Forwarded-For} é honrado para identificar o cliente. <b>Vazio por
 *                       omissão</b>: sem um proxy configurado explicitamente, o cabeçalho nunca é
 *                       confiável — é trivialmente forjável por qualquer chamador — e usa-se sempre
 *                       o IP da ligação TCP ({@link jakarta.servlet.ServletRequest#getRemoteAddr()}).
 * @param webhook        limite dedicado, mais apertado, para {@code /v1/webhooks/**} (endpoint
 *                       público que persiste o corpo do pedido antes de validar a assinatura —
 *                       aplicado além do limite global, não em substituição dele).
 */
@ConfigurationProperties(prefix = "servimatch.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        long capacity,
        Duration refillPeriod,
        String backend,
        List<String> trustedProxies,
        Webhook webhook
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
        trustedProxies = trustedProxies == null
                ? List.of()
                : trustedProxies.stream().filter(proxy -> proxy != null && !proxy.isBlank()).toList();
        if (webhook == null) {
            webhook = new Webhook(0, null);
        }
    }

    /**
     * @param capacity     nº máximo de pedidos ao webhook, por cliente, na janela de refil.
     * @param refillPeriod duração da janela de refil.
     */
    public record Webhook(long capacity, Duration refillPeriod) {
        public Webhook {
            if (capacity <= 0) {
                capacity = 20;
            }
            if (refillPeriod == null) {
                refillPeriod = Duration.ofMinutes(1);
            }
        }
    }
}
