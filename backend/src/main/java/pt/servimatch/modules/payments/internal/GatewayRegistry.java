package pt.servimatch.modules.payments.internal;

import org.springframework.stereotype.Component;
import pt.servimatch.modules.payments.GatewayCode;
import pt.servimatch.modules.payments.PaymentGateway;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolve o adaptador {@link PaymentGateway} pelo código de caminho
 * ({@code {gateway}} em {@code /v1/webhooks/payments/{gateway}} e no corpo
 * de {@code CreateSubscription.gateway}). PayPal é opcional (ADR-0007) e
 * não tem adaptador nesta onda — ausência tratada como "gateway não
 * suportado", nunca como erro 500.
 */
@Component
public class GatewayRegistry {

    private final Map<GatewayCode, PaymentGateway> gateways;

    public GatewayRegistry(List<PaymentGateway> gateways) {
        this.gateways = gateways.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentGateway::code, Function.identity()));
    }

    public Optional<PaymentGateway> find(GatewayCode code) {
        return Optional.ofNullable(gateways.get(code));
    }

    public Optional<PaymentGateway> find(String rawCode) {
        return GatewayCode.fromValue(rawCode).flatMap(this::find);
    }
}
