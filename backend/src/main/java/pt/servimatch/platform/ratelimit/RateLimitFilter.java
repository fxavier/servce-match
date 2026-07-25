package pt.servimatch.platform.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import pt.servimatch.platform.error.ProblemDetailsResponseWriter;
import pt.servimatch.platform.error.ProblemType;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Rede de segurança global contra abuso: limite por cliente (IP, com
 * suporte a {@code X-Forwarded-For} atrás de um proxy/load balancer) sobre
 * <b>todos</b> os pedidos. Corre antes da autenticação — protege também
 * endpoints públicos e o próprio processo de login-adjacent.
 *
 * <p>Limites mais apertados por endpoint (ex.: criação de pedidos, envio de
 * propostas) ficam ao critério de cada módulo de domínio via
 * {@link RateLimiterService}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final BucketResolver bucketResolver;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(BucketResolver bucketResolver, RateLimitProperties properties, ObjectMapper objectMapper) {
        this.bucketResolver = bucketResolver;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = "global:" + clientIp(request);
        ConsumptionProbe probe = bucketResolver
                .resolve(key, properties.capacity(), properties.refillPeriod())
                .tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.capacity()));
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        ProblemDetailsResponseWriter.write(
                response,
                objectMapper,
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemType.RATE_LIMITED,
                "Demasiados pedidos",
                "Excedeu o limite de pedidos para este cliente. Tente novamente mais tarde.");
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
