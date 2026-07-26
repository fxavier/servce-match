package pt.servimatch.modules.payments.web.dto;

import java.util.UUID;

/** Espelha {@code SubscriptionCheckout} no contrato. */
public record SubscriptionCheckoutResponse(
        UUID subscriptionId,
        String status,
        String checkoutUrl,
        PaymentReferenceDto paymentReference) {
}
