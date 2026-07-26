package pt.servimatch.modules.payments;

import java.util.Locale;
import java.util.Optional;

/**
 * Valores válidos de {@code gateway} — espelham o {@code CHECK} da base de
 * dados ({@code payment.gateway}, {@code subscription.gateway},
 * {@code payment_gateway_event.gateway}) e o enum do contrato
 * ({@code CreateSubscription.gateway}, path {@code /v1/webhooks/payments/{gateway}}).
 */
public enum GatewayCode {
    STRIPE("stripe"),
    EUPAGO("eupago"),
    IFTHENPAY("ifthenpay"),
    PAYPAL("paypal");

    private final String value;

    GatewayCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<GatewayCode> fromValue(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        for (GatewayCode code : values()) {
            if (code.value.equals(normalized)) {
                return Optional.of(code);
            }
        }
        return Optional.empty();
    }
}
