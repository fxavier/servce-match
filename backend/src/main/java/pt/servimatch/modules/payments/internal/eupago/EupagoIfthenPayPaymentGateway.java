package pt.servimatch.modules.payments.internal.eupago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import pt.servimatch.modules.payments.CheckoutRequest;
import pt.servimatch.modules.payments.CheckoutResult;
import pt.servimatch.modules.payments.GatewayCode;
import pt.servimatch.modules.payments.GatewayEvent;
import pt.servimatch.modules.payments.PaymentGateway;
import pt.servimatch.modules.payments.ReconciledPaymentState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador Eupago/IfthenPay (ADR-0007): MB WAY e Multibanco,
 * <b>*invoice-based*</b> — sem débito recorrente automático. Cada ciclo de
 * faturação gera uma <b>nova</b> referência/cobrança; a subscrição só
 * avança quando essa referência específica é paga e o webhook
 * correspondente chega e é verificado. Uma mesma instância desta classe
 * serve os dois fornecedores (código+credenciais passados no construtor —
 * ver {@code PaymentsConfiguration}); é a mesma integração, com endpoint e
 * chave diferentes, não uma terceira abstração.
 *
 * <p><b>Assunção documentada sobre o formato do callback</b> (a confirmar
 * contra a documentação oficial antes de produção — ver
 * {@link EupagoSignature}): JSON com {@code identifier} (o nosso
 * {@code correlationToken}, ecoado), {@code reference}, {@code entity},
 * {@code amount} (decimal, ex. {@code "45.00"}), {@code status}
 * ({@code paid}/{@code pending}/{@code failed}/{@code expired}) e
 * {@code dateTime} (ISO-8601).
 */
public class EupagoIfthenPayPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(EupagoIfthenPayPaymentGateway.class);

    private final GatewayCode code;
    private final RestClient restClient;
    private final String apiKey;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public EupagoIfthenPayPaymentGateway(GatewayCode code, RestClient restClient, String apiKey, String webhookSecret, ObjectMapper objectMapper) {
        this.code = code;
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayCode code() {
        return code;
    }

    @Override
    public CheckoutResult startCheckout(CheckoutRequest request) {
        String decimalAmount = BigDecimal.valueOf(request.amountCents(), 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        JsonNode response = restClient.post()
                .uri("/multibanco/create")
                .headers(h -> h.set("ApiKey", apiKey))
                .body(Map.of(
                        "identifier", request.correlationToken(),
                        "amount", decimalAmount,
                        "currency", request.currency(),
                        "description", request.planName()))
                .retrieve()
                .body(JsonNode.class);

        String entity = text(response, "entity");
        String reference = text(response, "reference");
        CheckoutResult.PaymentReference paymentReference = new CheckoutResult.PaymentReference(
                entity, reference, request.amountCents(), request.currency());
        // gateway_payment_id inicial = o próprio correlationToken; será substituído/confirmado
        // pelo identificador de transação real quando o webhook de pagamento chegar.
        return new CheckoutResult(null, paymentReference, null, null, request.correlationToken());
    }

    @Override
    public boolean verifySignature(byte[] rawBody, Map<String, String> headers) {
        String header = headerIgnoreCase(headers, "X-Signature");
        return EupagoSignature.verify(rawBody, header, webhookSecret);
    }

    @Override
    public String peekEventId(byte[] rawBody) {
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            String transactionId = text(node, "transactionId");
            if (transactionId != null) {
                return transactionId;
            }
            String reference = text(node, "reference");
            String identifier = text(node, "identifier");
            if (reference != null && identifier != null) {
                return identifier + ":" + reference;
            }
            return sha256(rawBody);
        } catch (Exception e) {
            return sha256(rawBody);
        }
    }

    @Override
    public GatewayEvent parseEvent(byte[] rawBody, Map<String, String> headers) {
        JsonNode node;
        try {
            node = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed " + code.value() + " callback payload", e);
        }
        String eventId = peekEventId(rawBody);
        String status = text(node, "status");
        String identifier = text(node, "identifier");
        String reference = text(node, "reference");
        String amountText = text(node, "amount");
        String currency = text(node, "currency");
        Instant occurredAt = parseDateTime(text(node, "dateTime"));

        Optional<UUID> subscriptionId = parseUuid(identifier);
        String gatewayPaymentId = reference != null ? reference : eventId;

        if (status == null) {
            return new GatewayEvent.Unrecognized(eventId, "unknown", occurredAt);
        }
        return switch (status.toLowerCase()) {
            case "paid", "success", "completed" -> {
                long amountCents = amountText == null ? 0 : new BigDecimal(amountText).movePointRight(2).longValueExact();
                Instant periodStart = occurredAt;
                Instant periodEnd = occurredAt.plusSeconds(30L * 24 * 3600);
                yield new GatewayEvent.PaymentSucceeded(eventId, subscriptionId, Optional.empty(), gatewayPaymentId,
                        amountCents, currency == null ? "EUR" : currency.toUpperCase(), periodStart, periodEnd, occurredAt);
            }
            case "failed", "declined", "expired", "cancelled" -> new GatewayEvent.PaymentFailed(
                    eventId, subscriptionId, Optional.empty(), gatewayPaymentId, status, occurredAt);
            case "pending" -> new GatewayEvent.Unrecognized(eventId, "pending", occurredAt);
            default -> new GatewayEvent.Unrecognized(eventId, status, occurredAt);
        };
    }

    @Override
    public ReconciledPaymentState reconcile(String correlationReference) {
        try {
            JsonNode node = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/multibanco/status").queryParam("identifier", correlationReference).build())
                    .headers(h -> h.set("ApiKey", apiKey))
                    .retrieve()
                    .body(JsonNode.class);
            String status = text(node, "status");
            if (status == null) {
                return new ReconciledPaymentState(ReconciledPaymentState.Status.UNKNOWN, Instant.now());
            }
            return switch (status.toLowerCase()) {
                case "paid", "success", "completed" -> new ReconciledPaymentState(ReconciledPaymentState.Status.PAID, Instant.now());
                case "failed", "declined", "cancelled" -> new ReconciledPaymentState(ReconciledPaymentState.Status.FAILED, Instant.now());
                case "expired" -> new ReconciledPaymentState(ReconciledPaymentState.Status.CANCELED, Instant.now());
                default -> new ReconciledPaymentState(ReconciledPaymentState.Status.PENDING, Instant.now());
            };
        } catch (Exception e) {
            log.warn("{} reconciliation lookup failed: {}", code.value(), e.getMessage());
            return new ReconciledPaymentState(ReconciledPaymentState.Status.UNKNOWN, Instant.now());
        }
    }

    private static Instant parseDateTime(String raw) {
        if (raw == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private static Optional<UUID> parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String headerIgnoreCase(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String sha256(byte[] bytes) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
