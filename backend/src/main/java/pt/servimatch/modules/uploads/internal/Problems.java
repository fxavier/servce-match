package pt.servimatch.modules.uploads.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.platform.error.ProblemDetailsSupport;
import pt.servimatch.platform.error.ProblemType;

/**
 * Fábrica de {@link ErrorResponseException} em formato RFC 9457 — mesmo
 * padrão duplicado deliberadamente em cada módulo, ver nota equivalente em
 * {@code pt.servimatch.modules.requests.internal.Problems}.
 */
final class Problems {

    private Problems() {
    }

    static ErrorResponseException unprocessable(String detail) {
        return of(HttpStatus.UNPROCESSABLE_ENTITY, ProblemType.VALIDATION, "Dados inválidos", detail);
    }

    static ErrorResponseException payloadTooLarge(String detail) {
        return of(HttpStatus.PAYLOAD_TOO_LARGE, ProblemType.PAYLOAD_TOO_LARGE, "Ficheiro demasiado grande", detail);
    }

    static ErrorResponseException unsupportedMediaType(String detail) {
        return of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ProblemType.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo não suportado", detail);
    }

    private static ErrorResponseException of(HttpStatus status, String type, String title, String detail) {
        return new ErrorResponseException(status, ProblemDetailsSupport.of(status, type, title, detail), null);
    }
}
