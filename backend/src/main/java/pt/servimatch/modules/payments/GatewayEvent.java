package pt.servimatch.modules.payments;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Evento de gateway normalizado, produzido por
 * {@link PaymentGateway#parseEvent} depois de a assinatura já ter sido
 * verificada. {@code occurredAt} é o instante do lado do <b>gateway</b>
 * (não o instante de receção) — é o que permite detetar e ignorar eventos
 * fora de ordem sem regredir o estado local (skill
 * {@code payment-webhook-hardening}).
 *
 * <p>{@code subscriptionId} vem preenchido quando o próprio evento traz a
 * correlação (Stripe: {@code client_reference_id} de uma Checkout Session
 * concluída; Eupago/IfthenPay: identificador do pedido ecoado). Quando
 * ausente (ex.: renovação automática Stripe via fatura, que só traz o
 * {@code gatewaySubscriptionId} do lado do Stripe), o processador
 * correlaciona por {@code Subscription.gatewaySubscriptionId}.
 */
public sealed interface GatewayEvent {

    String rawEventId();

    Instant occurredAt();

    record PaymentSucceeded(
            String rawEventId,
            Optional<UUID> subscriptionId,
            Optional<String> gatewaySubscriptionId,
            String gatewayPaymentId,
            long amountCents,
            String currency,
            Instant periodStart,
            Instant periodEnd,
            Instant occurredAt) implements GatewayEvent {
    }

    record PaymentFailed(
            String rawEventId,
            Optional<UUID> subscriptionId,
            Optional<String> gatewaySubscriptionId,
            String gatewayPaymentId,
            String reason,
            Instant occurredAt) implements GatewayEvent {
    }

    record SubscriptionCanceledUpstream(
            String rawEventId,
            Optional<UUID> subscriptionId,
            Optional<String> gatewaySubscriptionId,
            Instant occurredAt) implements GatewayEvent {
    }

    /** Tipo de evento reconhecido pelo gateway mas sem efeito de domínio (ex.: `charge.dispute.created`). Marcado IGNORED. */
    record Unrecognized(String rawEventId, String eventType, Instant occurredAt) implements GatewayEvent {
    }
}
