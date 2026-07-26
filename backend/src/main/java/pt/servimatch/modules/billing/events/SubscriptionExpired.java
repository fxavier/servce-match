package pt.servimatch.modules.billing.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado quando uma subscrição {@code ACTIVE} chega ao fim do período
 * sem renovação confirmada (job de expiração ou, no caso de métodos
 * *invoice-based* como Multibanco, referência de renovação não paga a
 * tempo — ADR-0007 §12.2). Estado terminal: {@code EXPIRED} nunca volta a
 * {@code ACTIVE} pela mesma subscrição.
 */
public record SubscriptionExpired(
        UUID subscriptionId,
        UUID providerId,
        Instant occurredAt) {
}
