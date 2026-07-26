package pt.servimatch.modules.payments;

import java.util.UUID;

/**
 * Pedido de início de pagamento de um plano. {@code correlationToken} é o
 * identificador que o nosso sistema gera e que espera ver ecoado no
 * webhook correspondente (Stripe: {@code client_reference_id} da Checkout
 * Session; Eupago/IfthenPay: identificador do pedido/referência) —
 * mecanismo único de correlação evento→subscrição, independente do
 * gateway.
 */
public record CheckoutRequest(
        UUID subscriptionId,
        UUID providerId,
        String correlationToken,
        long amountCents,
        String currency,
        String planName,
        String returnUrl) {
}
