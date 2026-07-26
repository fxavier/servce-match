package pt.servimatch.modules.payments.internal;

/** Resultado do processamento de um webhook — traduzido para HTTP pelo controller. */
public record IngestOutcome(Result result) {

    public enum Result {
        UNKNOWN_GATEWAY,
        BAD_REQUEST,
        UNAUTHORIZED,
        DUPLICATE,
        PROCESSED
    }

    public static IngestOutcome of(Result result) {
        return new IngestOutcome(result);
    }
}
