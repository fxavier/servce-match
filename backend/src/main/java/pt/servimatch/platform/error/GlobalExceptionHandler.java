package pt.servimatch.platform.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Handler central de erros do backend: converte qualquer exceção não
 * apanhada explicitamente por um módulo de domínio para RFC 9457 Problem
 * Details, com {@code type} estável ({@link ProblemType}) e
 * {@code correlationId}.
 *
 * <p><b>Nunca</b> devolve stack trace, nome de classe, SQL ou PII no corpo
 * do erro — esses detalhes vão para o log estruturado (correlacionável por
 * {@code correlation_id}), nunca para a resposta HTTP.
 *
 * <p>Erros que ocorrem antes do {@code DispatcherServlet} (autenticação,
 * autorização ao nível do filtro, rate limiting, idempotência) não passam
 * por aqui — ver {@code pt.servimatch.platform.security.ProblemDetailsAuthenticationEntryPoint},
 * {@code ProblemDetailsAccessDeniedHandler}, {@code RateLimitFilter} e
 * {@code IdempotencyFilter}, que usam {@link ProblemDetailsResponseWriter}
 * para o mesmo formato.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Ponto único por onde passam (quase) todas as respostas de erro produzidas
     * pela superclasse do Spring MVC. Garante {@code type} e {@code correlationId}
     * mesmo quando o corpo já vem preenchido pelo comportamento por omissão do
     * Spring (que usa {@code about:blank}).
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problemDetail) {
            if (problemDetail.getType() == null || "about:blank".equals(problemDetail.getType().toString())) {
                ProblemDetailsSupport.assignType(problemDetail, typeFor(HttpStatus.valueOf(statusCode.value())));
            } else {
                ProblemDetailsSupport.applyCorrelationId(problemDetail);
            }
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetailsSupport.of(
                HttpStatus.valueOf(status.value()),
                ProblemType.VALIDATION,
                "Dados inválidos",
                "Um ou mais campos do pedido são inválidos.");
        problemDetail.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fieldError(fe.getField(), fe.getDefaultMessage()))
                .toList());
        return createResponseEntity(problemDetail, headers, status, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetailsSupport.of(
                HttpStatus.BAD_REQUEST,
                ProblemType.VALIDATION,
                "Dados inválidos",
                "Um ou mais parâmetros do pedido são inválidos.");
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::violationToFieldError)
                .toList();
        problemDetail.setProperty("errors", errors);
        return createResponseEntity(problemDetail, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetailsSupport.of(
                HttpStatus.FORBIDDEN,
                ProblemType.FORBIDDEN,
                "Acesso negado",
                "Não tem permissão para efetuar esta operação.");
        return createResponseEntity(problemDetail, new HttpHeaders(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetailsSupport.of(
                HttpStatus.UNAUTHORIZED,
                ProblemType.UNAUTHENTICATED,
                "Não autenticado",
                "É necessário um token de acesso válido.");
        return createResponseEntity(problemDetail, new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation (correlation_id={})", pt.servimatch.platform.observability.CorrelationIdSupport.currentOrNull(), ex);
        ProblemDetail problemDetail = ProblemDetailsSupport.of(
                HttpStatus.CONFLICT,
                ProblemType.CONFLICT,
                "Conflito de estado",
                "O pedido entra em conflito com o estado atual do recurso.");
        return createResponseEntity(problemDetail, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    /** Rede de segurança final: nunca deixa uma exceção não tratada expor detalhe interno. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception (correlation_id={})", pt.servimatch.platform.observability.CorrelationIdSupport.currentOrNull(), ex);
        ProblemDetail problemDetail = ProblemDetailsSupport.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemType.INTERNAL,
                "Erro interno",
                "Ocorreu um erro inesperado. A equipa foi notificada.");
        return createResponseEntity(problemDetail, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private static Map<String, String> fieldError(String field, String message) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("field", field);
        map.put("message", Objects.requireNonNullElse(message, "Valor inválido."));
        return map;
    }

    private static Map<String, String> violationToFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        return fieldError(path, violation.getMessage());
    }

    private static String typeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST, UNPROCESSABLE_ENTITY -> ProblemType.VALIDATION;
            case UNAUTHORIZED -> ProblemType.UNAUTHENTICATED;
            case FORBIDDEN -> ProblemType.FORBIDDEN;
            case NOT_FOUND -> ProblemType.NOT_FOUND;
            case CONFLICT -> ProblemType.CONFLICT;
            case TOO_MANY_REQUESTS -> ProblemType.RATE_LIMITED;
            case PAYLOAD_TOO_LARGE -> ProblemType.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> ProblemType.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? ProblemType.INTERNAL : ProblemType.VALIDATION;
        };
    }
}
