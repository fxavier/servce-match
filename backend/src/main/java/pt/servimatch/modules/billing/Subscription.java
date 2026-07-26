package pt.servimatch.modules.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * Subscrição de um prestador a um {@link SubscriptionPlan} (ARQUITETURA
 * §12.1, §9.2). {@code gatewaySubscriptionId} é o identificador estável do
 * lado do gateway usado para correlacionar eventos de renovação (Stripe:
 * {@code subscription.id}; Eupago/IfthenPay: não aplicável — a correlação
 * por método *invoice-based* usa antes {@code Payment.gatewayPaymentId} por
 * ciclo, ver {@code modules.payments}).
 */
public record Subscription(
        UUID id,
        UUID providerId,
        UUID planId,
        SubscriptionStatus status,
        String gateway,
        String gatewayCustomerId,
        String gatewaySubscriptionId,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant createdAt,
        Instant updatedAt) {
}
