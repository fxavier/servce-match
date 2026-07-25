package pt.servimatch.platform.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import pt.servimatch.platform.observability.CorrelationIdSupport;

/**
 * Construção consistente de {@link ProblemDetail} (RFC 9457) em todo o
 * backend: {@code type} sob {@link ProblemType#BASE_URI} e
 * {@code correlationId} sempre presente quando disponível. Nunca inclui
 * stack trace, nome de classe, SQL ou PII.
 *
 * <p>Usado quer pelo {@link GlobalExceptionHandler} (exceções apanhadas
 * dentro do {@code DispatcherServlet}), quer por componentes que escrevem a
 * resposta de erro diretamente a partir da cadeia de filtros (autenticação,
 * rate limiting, idempotência), onde não há oportunidade de passar por um
 * {@code @ExceptionHandler}.
 */
public final class ProblemDetailsSupport {

    private ProblemDetailsSupport() {
    }

    public static ProblemDetail of(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(java.net.URI.create(type));
        problemDetail.setTitle(title);
        applyCorrelationId(problemDetail);
        return problemDetail;
    }

    /** Atribui o {@code type} a um {@link ProblemDetail} já existente, sem alterar o resto. */
    public static void assignType(ProblemDetail problemDetail, String type) {
        problemDetail.setType(java.net.URI.create(type));
        applyCorrelationId(problemDetail);
    }

    /** Completa um {@link ProblemDetail} produzido pelo Spring (ex.: {@code about:blank}) com o correlation id. */
    public static void applyCorrelationId(ProblemDetail problemDetail) {
        String correlationId = CorrelationIdSupport.currentOrNull();
        if (correlationId != null) {
            problemDetail.setProperty("correlationId", correlationId);
        }
    }
}
