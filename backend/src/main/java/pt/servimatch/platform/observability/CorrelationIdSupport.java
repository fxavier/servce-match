package pt.servimatch.platform.observability;

import org.slf4j.MDC;

/**
 * Constantes e acesso partilhado ao correlation id do pedido atual.
 *
 * <p>O correlation id é distinto do trace id do OpenTelemetry: é um
 * identificador de negócio, gerado ou aceite do cliente em
 * {@value #HEADER_NAME}, propagado via MDC (e por isso presente em todos os
 * logs estruturados desse pedido — ver {@code logging.structured.format} em
 * {@code application.yml}) e devolvido nas respostas, incluindo nos corpos
 * de erro RFC 9457. Nunca contém PII.
 */
public final class CorrelationIdSupport {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlation_id";

    private CorrelationIdSupport() {
    }

    /** Correlation id do pedido em curso, ou {@code null} fora de um pedido HTTP. */
    public static String currentOrNull() {
        return MDC.get(MDC_KEY);
    }
}
