package pt.servimatch.modules.payments.web.dto;

import pt.servimatch.modules.billing.Subscription;

import java.time.Instant;
import java.util.UUID;

/**
 * Espelha o schema {@code Subscription} do contrato ({@code docs/api/openapi.yaml},
 * {@code GET /v1/subscriptions/me}). {@code currentPeriodStart}/{@code currentPeriodEnd}
 * ficam a {@code null} para uma subscrição ainda {@code PENDING} (nunca teve
 * período) — o schema não os marca como {@code required}.
 *
 * <p>Este DTO não é usado para decidir nada: é uma cópia dos factos da
 * subscrição para o prestador ver. O <em>gating</em> em si nunca lê este
 * corpo de resposta — é resolvido no servidor por
 * {@code SubscriptionLifecycle.isVisibilityEligible}/{@code hasActiveSubscription}
 * (CLAUDE.md §4: a UI só espelha o que o servidor decide).
 */
public record SubscriptionResponse(
        UUID id,
        UUID providerId,
        UUID planId,
        String status,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd) {

    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.id(),
                subscription.providerId(),
                subscription.planId(),
                subscription.status().name(),
                subscription.currentPeriodStart(),
                subscription.currentPeriodEnd(),
                subscription.cancelAtPeriodEnd());
    }
}
