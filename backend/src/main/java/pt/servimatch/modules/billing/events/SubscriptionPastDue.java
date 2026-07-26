package pt.servimatch.modules.billing.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado quando um pagamento falha e a subscrição entra (ou permanece)
 * em {@code PAST_DUE} — estado de tolerância, não um limbo indefinido (ver
 * {@code BillingProperties}). Ainda concede visibilidade
 * ({@link pt.servimatch.modules.billing.SubscriptionStatus#grantsVisibility()}).
 */
public record SubscriptionPastDue(
        UUID subscriptionId,
        UUID providerId,
        Instant occurredAt) {
}
