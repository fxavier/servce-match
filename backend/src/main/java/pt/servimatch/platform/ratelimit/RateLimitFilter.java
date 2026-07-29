package pt.servimatch.platform.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import pt.servimatch.platform.error.ProblemDetailsResponseWriter;
import pt.servimatch.platform.error.ProblemType;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Rede de segurança global contra abuso: limite por cliente sobre
 * <b>todos</b> os pedidos, mais um limite dedicado, mais apertado, para
 * {@code /v1/webhooks/**} (endpoint público que persiste o corpo do pedido
 * antes de validar a assinatura — ver
 * {@code pt.servimatch.modules.payments.web.PaymentWebhookController}).
 * Corre antes da autenticação — protege também endpoints públicos e o
 * próprio processo de login-adjacent.
 *
 * <p>{@code X-Forwarded-For} só é honrado para identificar o cliente quando
 * o pedido chega de um proxy listado em
 * {@code servimatch.rate-limit.trusted-proxies}; caso contrário (omissão:
 * lista vazia, "sem proxy configurado") usa-se sempre
 * {@link HttpServletRequest#getRemoteAddr()}. Qualquer chamador pode enviar
 * um {@code X-Forwarded-For} arbitrário — sem esta verificação, o limite é
 * trivialmente contornável rodando o valor do cabeçalho.
 *
 * <p>Limites mais apertados por endpoint (ex.: criação de pedidos, envio de
 * propostas) ficam ao critério de cada módulo de domínio via
 * {@link RateLimiterService}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String WEBHOOK_PATH_PREFIX = "/v1/webhooks/";

    private final BucketResolver bucketResolver;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final List<IpAddressMatcher> trustedProxyMatchers;

    public RateLimitFilter(BucketResolver bucketResolver, RateLimitProperties properties, ObjectMapper objectMapper) {
        this.bucketResolver = bucketResolver;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.trustedProxyMatchers = properties.trustedProxies().stream().map(IpAddressMatcher::new).toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);

        ConsumptionProbe globalProbe = consume("global:" + ip, properties.capacity(), properties.refillPeriod());
        if (!globalProbe.isConsumed()) {
            reject(response, globalProbe, properties.capacity());
            return;
        }

        if (request.getRequestURI().startsWith(WEBHOOK_PATH_PREFIX)) {
            RateLimitProperties.Webhook webhook = properties.webhook();
            ConsumptionProbe webhookProbe = consume("webhook:" + ip, webhook.capacity(), webhook.refillPeriod());
            if (!webhookProbe.isConsumed()) {
                reject(response, webhookProbe, webhook.capacity());
                return;
            }
            response.setHeader("X-RateLimit-Limit", String.valueOf(webhook.capacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(webhookProbe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(globalProbe.getRemainingTokens()));
        filterChain.doFilter(request, response);
    }

    private ConsumptionProbe consume(String key, long capacity, Duration period) {
        Bucket bucket = bucketResolver.resolve(key, capacity, period);
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private void reject(HttpServletResponse response, ConsumptionProbe probe, long capacity) throws IOException {
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        ProblemDetailsResponseWriter.write(
                response,
                objectMapper,
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemType.RATE_LIMITED,
                "Demasiados pedidos",
                "Excedeu o limite de pedidos para este cliente. Tente novamente mais tarde.");
    }

    /**
     * IP do cliente para efeitos de rate limiting. {@code X-Forwarded-For} só
     * é considerado quando {@link HttpServletRequest#getRemoteAddr()} —
     * quem estabeleceu a ligação TCP — corresponde a um proxy de confiança
     * configurado; caso contrário o cabeçalho é ignorado (é apenas um valor
     * de entrada do chamador, nunca prova de origem).
     */
    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || trustedProxyMatchers.isEmpty()) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxyMatchers) {
            try {
                if (matcher.matches(remoteAddr)) {
                    return true;
                }
            } catch (IllegalArgumentException malformedRemoteAddr) {
                // Endereço da ligação TCP num formato inesperado: nunca confiar.
                return false;
            }
        }
        return false;
    }
}
