package pt.servimatch.modules.providers.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.platform.error.ProblemDetailsSupport;
import pt.servimatch.platform.error.ProblemType;

/**
 * Fábrica de {@link ErrorResponseException} em formato RFC 9457 — ver nota
 * equivalente em {@code pt.servimatch.modules.requests.internal.Problems}:
 * duplicado deliberadamente por módulo (é código {@code internal}, não pode
 * viver num único sítio partilhado sem introduzir uma dependência de módulo
 * nova).
 */
final class Problems {

    private Problems() {
    }

    static ErrorResponseException notFound(String detail) {
        return of(HttpStatus.NOT_FOUND, ProblemType.NOT_FOUND, "Não encontrado", detail);
    }

    static ErrorResponseException forbidden(String detail) {
        return of(HttpStatus.FORBIDDEN, ProblemType.FORBIDDEN, "Acesso negado", detail);
    }

    static ErrorResponseException unprocessable(String detail) {
        return of(HttpStatus.UNPROCESSABLE_ENTITY, ProblemType.VALIDATION, "Dados inválidos", detail);
    }

    static ErrorResponseException conflict(String detail) {
        return of(HttpStatus.CONFLICT, ProblemType.CONFLICT, "Conflito de estado", detail);
    }

    private static ErrorResponseException of(HttpStatus status, String type, String title, String detail) {
        return new ErrorResponseException(status, ProblemDetailsSupport.of(status, type, title, detail), null);
    }
}
