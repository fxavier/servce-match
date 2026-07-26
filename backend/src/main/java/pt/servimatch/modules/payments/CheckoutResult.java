package pt.servimatch.modules.payments;

/**
 * Resultado de {@link PaymentGateway#startCheckout}. Exatamente um de
 * {@code checkoutUrl} (Stripe — redirecionar) ou {@code paymentReference}
 * (Eupago/IfthenPay — Multibanco/MB WAY) é não-nulo, espelhando
 * {@code SubscriptionCheckout} no contrato. {@code gatewayCustomerId}/
 * {@code gatewaySubscriptionId} podem ser conhecidos de imediato (Stripe
 * cria o cliente antes do checkout) ou ficar por preencher até ao webhook.
 */
public record CheckoutResult(
        String checkoutUrl,
        PaymentReference paymentReference,
        String gatewayCustomerId,
        String gatewaySubscriptionId,
        String gatewayPaymentId) {

    public record PaymentReference(String entity, String reference, long amountCents, String currency) {
    }
}
