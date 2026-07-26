package pt.servimatch.modules.proposals.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.platform.error.ProblemDetailsSupport;
import pt.servimatch.platform.error.ProblemType;

/** Ver {@code pt.servimatch.modules.requests.internal.Problems} para a nota de design. */
final class Problems {

    private Problems() {
    }

    static ErrorResponseException notFound(String detail) {
        return of(HttpStatus.NOT_FOUND, ProblemType.NOT_FOUND, "Não encontrado", detail);
    }

    static ErrorResponseException conflict(String detail) {
        return of(HttpStatus.CONFLICT, ProblemType.CONFLICT, "Conflito de estado", detail);
    }

    static ErrorResponseException forbidden(String detail) {
        return of(HttpStatus.FORBIDDEN, ProblemType.FORBIDDEN, "Acesso negado", detail);
    }

    static ErrorResponseException unprocessable(String detail) {
        return of(HttpStatus.UNPROCESSABLE_ENTITY, ProblemType.VALIDATION, "Dados inválidos", detail);
    }

    /** {@code 403} com o {@code type} específico do contrato para subscrição inativa. */
    static ErrorResponseException subscriptionRequired(String detail) {
        return of(HttpStatus.FORBIDDEN, ProblemType.BASE_URI + "subscription-required", "Subscrição necessária", detail);
    }

    private static ErrorResponseException of(HttpStatus status, String type, String title, String detail) {
        return new ErrorResponseException(status, ProblemDetailsSupport.of(status, type, title, detail), null);
    }
}
