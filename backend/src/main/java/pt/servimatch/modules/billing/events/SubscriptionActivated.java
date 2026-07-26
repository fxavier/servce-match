package pt.servimatch.modules.billing.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado quando uma subscrição transita para {@code ACTIVE} (checkout
 * inicial pago ou recuperação a partir de {@code PAST_DUE}). Consumidores
 * (ex.: {@code providers}, para {@code visibility_state}; {@code matching})
 * reagem de forma assíncrona — ver CLAUDE.md §4 ("gating... via API pública
 * do módulo ou evento"). Publicado na mesma transação que a escrita
 * (Spring Modulith Event Publication Registry, entrega at-least-once); os
 * consumidores têm de ser idempotentes.
 */
public record SubscriptionActivated(
        UUID subscriptionId,
        UUID providerId,
        UUID planId,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant occurredAt) {
}
