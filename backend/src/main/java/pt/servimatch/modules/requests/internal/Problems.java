package pt.servimatch.modules.requests.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.platform.error.ProblemDetailsSupport;
import pt.servimatch.platform.error.ProblemType;

/**
 * Fábrica de {@link ErrorResponseException} em formato RFC 9457, reutilizando
 * o catálogo central {@link ProblemType} e {@link ProblemDetailsSupport}
 * (ambos de {@code pt.servimatch.platform.error}, propriedade de
 * {@code backend-platform} — só leitura aqui). {@link ErrorResponseException}
 * é apanhada pelo {@code GlobalExceptionHandler} central independentemente
 * de onde é lançada (é o mecanismo standard do Spring MVC, não um segundo
 * mecanismo de erro); por isso o domínio pode lançá-la diretamente a partir
 * de qualquer camada, incluindo quando chamada síncronamente por outro
 * módulo.
 *
 * <p>Duplicado (pequeno, deliberadamente) em cada módulo que precisa dele —
 * ver nota equivalente em {@code proposals}, {@code bookings}, {@code reviews}:
 * é código interno (não visível fora do módulo), por isso não pode viver
 * num único sítio partilhado sem introduzir uma dependência de módulo nova.
 */
final class Problems {

    private Problems() {
    }

    static ErrorResponseException notFound(String detail) {
        return of(HttpStatus.NOT_FOUND, ProblemType.NOT_FOUND, "Não encontrado", detail);
    }

    /**
     * {@code 400}: parâmetro de consulta inválido (ex. {@code status} fora
     * do enum {@code RequestStatus}). Nunca degradar um filtro inválido
     * para lista vazia silenciosa — o cliente acreditaria que não há dados
     * (CLAUDE.md/relatório de entrega).
     */
    static ErrorResponseException badRequest(String detail) {
        return of(HttpStatus.BAD_REQUEST, ProblemType.VALIDATION, "Parâmetros inválidos", detail);
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

    private static ErrorResponseException of(HttpStatus status, String type, String title, String detail) {
        return new ErrorResponseException(status, ProblemDetailsSupport.of(status, type, title, detail), null);
    }
}
