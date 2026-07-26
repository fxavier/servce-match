package pt.servimatch.modules.search.internal;

/**
 * Parâmetro de {@code GET /v1/search/providers} sintaticamente inválido ou
 * em combinação inválida (ex.: {@code lat} sem {@code lon}, {@code cursor}
 * corrompido). Mapeado para RFC 9457 400 pelo {@code @ExceptionHandler}
 * local de {@link SearchController} — não é um dos tipos que
 * {@code pt.servimatch.platform.error.GlobalExceptionHandler} já trata, e
 * esse ficheiro não é de escrita deste módulo.
 */
final class InvalidSearchParametersException extends RuntimeException {

    private final String field;

    InvalidSearchParametersException(String field, String message) {
        super(message);
        this.field = field;
    }

    String field() {
        return field;
    }
}
