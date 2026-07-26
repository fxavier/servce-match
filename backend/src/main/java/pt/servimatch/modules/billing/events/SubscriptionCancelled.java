package pt.servimatch.modules.billing.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado quando uma subscrição é cancelada — pedido explícito do
 * prestador (via {@code cancel_at_period_end}, aplicado no fim do período)
 * ou esgotamento da janela/tentativas de {@code PAST_DUE}. Estado terminal.
 */
public record SubscriptionCancelled(
        UUID subscriptionId,
        UUID providerId,
        Instant occurredAt) {
}
