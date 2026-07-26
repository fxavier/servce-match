package pt.servimatch.modules.payments;

/**
 * Classificação de recorrência por gateway (ADR-0007 §"Recorrência por
 * método de pagamento"). Usada para decidir a política correta ao fim de
 * um período: cartão (Stripe) tem auto-renew/dunning nativo → tolerância
 * via {@code PAST_DUE}; Multibanco/MB WAY (Eupago/IfthenPay) são
 * *invoice-based* → sem pagamento da referência a tempo, expira
 * diretamente. Modelar Multibanco como cartão é o erro estrutural mais
 * provável deste módulo — esta classe existe para que essa decisão nunca
 * fique implícita/duplicada.
 */
public final class GatewayFamily {

    private GatewayFamily() {
    }

    public static boolean isInvoiceBased(String gatewayValue) {
        return GatewayCode.fromValue(gatewayValue)
                .map(code -> code == GatewayCode.EUPAGO || code == GatewayCode.IFTHENPAY)
                .orElse(false);
    }

    public static boolean isRecurring(String gatewayValue) {
        return GatewayCode.fromValue(gatewayValue).map(code -> code == GatewayCode.STRIPE).orElse(false);
    }
}
