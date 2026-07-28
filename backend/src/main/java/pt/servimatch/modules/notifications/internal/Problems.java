package pt.servimatch.modules.notifications.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.platform.error.ProblemDetailsSupport;
import pt.servimatch.platform.error.ProblemType;

/**
 * Fábrica de {@link ErrorResponseException} em formato RFC 9457 — mesmo
 * padrão duplicado deliberadamente em cada módulo, ver nota equivalente em
 * {@code pt.servimatch.modules.uploads.internal.Problems}.
 */
final class Problems {

    private Problems() {
    }

    static ErrorResponseException notFound(String detail) {
        return of(HttpStatus.NOT_FOUND, ProblemType.NOT_FOUND, "Não encontrado", detail);
    }

    private static ErrorResponseException of(HttpStatus status, String type, String title, String detail) {
        return new ErrorResponseException(status, ProblemDetailsSupport.of(status, type, title, detail), null);
    }
}
