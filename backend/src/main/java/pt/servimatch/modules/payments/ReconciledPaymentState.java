package pt.servimatch.modules.payments;

import java.time.Instant;

/** Estado observado diretamente no gateway (fonte de verdade) para o job de reconciliação. */
public record ReconciledPaymentState(Status status, Instant occurredAt) {

    public enum Status {PAID, FAILED, PENDING, CANCELED, UNKNOWN}
}
