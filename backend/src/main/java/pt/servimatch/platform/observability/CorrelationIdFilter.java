package pt.servimatch.platform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Garante que todo o pedido tem um {@code correlation_id}: aceita
 * {@value CorrelationIdSupport#HEADER_NAME} do cliente se for um valor
 * plausível, ou gera um novo. Publica-o no MDC (propagando para todos os
 * logs estruturados do pedido) e ecoa-o na resposta.
 *
 * <p>Corre o mais cedo possível na cadeia de filtros — antes da autenticação
 * — para que também os 401 fiquem correlacionáveis. Não depende de
 * autenticação nem grava PII.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final int MAX_LENGTH = 128;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(CorrelationIdSupport.HEADER_NAME));
        MDC.put(CorrelationIdSupport.MDC_KEY, correlationId);
        response.setHeader(CorrelationIdSupport.HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIdSupport.MDC_KEY);
        }
    }

    private String resolve(String headerValue) {
        if (headerValue != null && !headerValue.isBlank() && headerValue.length() <= MAX_LENGTH) {
            return headerValue.trim();
        }
        return UUID.randomUUID().toString();
    }
}
