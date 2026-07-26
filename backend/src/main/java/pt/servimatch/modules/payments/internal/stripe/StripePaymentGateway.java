package pt.servimatch.modules.payments.internal.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import pt.servimatch.modules.payments.CheckoutRequest;
import pt.servimatch.modules.payments.CheckoutResult;
import pt.servimatch.modules.payments.GatewayCode;
import pt.servimatch.modules.payments.GatewayEvent;
import pt.servimatch.modules.payments.PaymentGateway;
import pt.servimatch.modules.payments.ReconciledPaymentState;
import pt.servimatch.modules.payments.internal.PaymentsProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador Stripe Billing (ADR-0007): cartão com renovação automática
 * nativa. O <b>ciclo de vida vem do gateway</b> — este adaptador só
 * traduz Stripe ↔ domínio, nunca decide sozinho quando uma subscrição
 * fica ativa (isso é sempre resultado de um webhook verificado, aplicado
 * pelo processador em {@code modules.billing}).
 *
 * <p>Checkout via <em>Checkout Session</em> em modo {@code subscription}
 * com {@code price_data} ad-hoc (sem depender de um {@code Price}
 * pré-criado no Stripe Dashboard, que exigiria mapear cada
 * {@code SubscriptionPlan} a um {@code stripe_price_id} — fora do schema
 * atual). {@code client_reference_id} carrega o nosso
 * {@code subscriptionId}, ecoado em {@code checkout.session.completed}.
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    private final RestClient restClient;
    private final PaymentsProperties properties;
    private final ObjectMapper objectMapper;

    public StripePaymentGateway(RestClient.Builder restClientBuilder, PaymentsProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(properties.stripeBaseUrl()).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayCode code() {
        return GatewayCode.STRIPE;
    }

    @Override
    public CheckoutResult startCheckout(CheckoutRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("client_reference_id", request.correlationToken());
        form.add("success_url", request.returnUrl() + (request.returnUrl().contains("?") ? "&" : "?") + "checkout=success");
        form.add("cancel_url", request.returnUrl() + (request.returnUrl().contains("?") ? "&" : "?") + "checkout=cancelled");
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", request.currency().toLowerCase());
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(request.amountCents()));
        form.add("line_items[0][price_data][recurring][interval]", "month");
        form.add("line_items[0][price_data][product_data][name]", request.planName());

        JsonNode session = restClient.post()
                .uri("/v1/checkout/sessions")
                .headers(headers -> headers.setBasicAuth(properties.stripeApiKey(), ""))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        String sessionId = text(session, "id");
        String url = text(session, "url");
        String customerId = text(session, "customer");
        return new CheckoutResult(url, null, customerId, null, sessionId);
    }

    @Override
    public boolean verifySignature(byte[] rawBody, Map<String, String> headers) {
        String header = headerIgnoreCase(headers, "Stripe-Signature");
        return StripeSignature.verify(rawBody, header, properties.stripeWebhookSecret(), properties.stripeWebhookToleranceSeconds());
    }

    @Override
    public String peekEventId(byte[] rawBody) {
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            String id = text(node, "id");
            return id != null ? id : sha256(rawBody);
        } catch (Exception e) {
            return sha256(rawBody);
        }
    }

    @Override
    public GatewayEvent parseEvent(byte[] rawBody, Map<String, String> headers) {
        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed Stripe event payload", e);
        }
        String eventId = text(event, "id");
        String type = text(event, "type");
        long createdEpoch = event.path("created").asLong(Instant.now().getEpochSecond());
        Instant occurredAt = Instant.ofEpochSecond(createdEpoch);
        JsonNode dataObject = event.path("data").path("object");

        return switch (type == null ? "" : type) {
            case "checkout.session.completed" -> {
                Optional<UUID> subscriptionId = parseUuid(text(dataObject, "client_reference_id"));
                Optional<String> gatewaySubscriptionId = Optional.ofNullable(text(dataObject, "subscription"));
                String paymentId = text(dataObject, "id");
                long amount = dataObject.path("amount_total").asLong(0);
                String currency = text(dataObject, "currency");
                yield new GatewayEvent.PaymentSucceeded(
                        eventId, subscriptionId, gatewaySubscriptionId, paymentId,
                        amount, currency == null ? "EUR" : currency.toUpperCase(),
                        occurredAt, occurredAt.plusSeconds(30L * 24 * 3600), occurredAt);
            }
            case "invoice.payment_succeeded" -> {
                Optional<String> gatewaySubscriptionId = Optional.ofNullable(text(dataObject, "subscription"));
                String paymentId = text(dataObject, "id");
                long amount = dataObject.path("amount_paid").asLong(0);
                String currency = text(dataObject, "currency");
                Instant periodStart = dataObject.path("period_start").isMissingNode() ? occurredAt
                        : Instant.ofEpochSecond(dataObject.path("period_start").asLong());
                Instant periodEnd = dataObject.path("period_end").isMissingNode() ? occurredAt.plusSeconds(30L * 24 * 3600)
                        : Instant.ofEpochSecond(dataObject.path("period_end").asLong());
                yield new GatewayEvent.PaymentSucceeded(
                        eventId, Optional.empty(), gatewaySubscriptionId, paymentId,
                        amount, currency == null ? "EUR" : currency.toUpperCase(), periodStart, periodEnd, occurredAt);
            }
            case "invoice.payment_failed" -> {
                Optional<String> gatewaySubscriptionId = Optional.ofNullable(text(dataObject, "subscription"));
                String paymentId = text(dataObject, "id");
                yield new GatewayEvent.PaymentFailed(eventId, Optional.empty(), gatewaySubscriptionId, paymentId, "invoice_payment_failed", occurredAt);
            }
            case "customer.subscription.deleted" -> {
                Optional<String> gatewaySubscriptionId = Optional.ofNullable(text(dataObject, "id"));
                yield new GatewayEvent.SubscriptionCanceledUpstream(eventId, Optional.empty(), gatewaySubscriptionId, occurredAt);
            }
            default -> new GatewayEvent.Unrecognized(eventId, type == null ? "unknown" : type, occurredAt);
        };
    }

    @Override
    public ReconciledPaymentState reconcile(String correlationReference) {
        try {
            String path = correlationReference != null && correlationReference.startsWith("sub_")
                    ? "/v1/subscriptions/" + correlationReference
                    : "/v1/checkout/sessions/" + correlationReference;
            JsonNode node = restClient.get()
                    .uri(path)
                    .headers(h -> h.setBasicAuth(properties.stripeApiKey(), ""))
                    .retrieve()
                    .body(JsonNode.class);
            String status = text(node, "status");
            String paymentStatus = text(node, "payment_status");
            if ("complete".equals(status) || "paid".equals(paymentStatus) || "active".equals(status)) {
                return new ReconciledPaymentState(ReconciledPaymentState.Status.PAID, Instant.now());
            }
            if ("canceled".equals(status) || "expired".equals(status)) {
                return new ReconciledPaymentState(ReconciledPaymentState.Status.CANCELED, Instant.now());
            }
            if ("past_due".equals(status) || "unpaid".equals(status)) {
                return new ReconciledPaymentState(ReconciledPaymentState.Status.FAILED, Instant.now());
            }
            return new ReconciledPaymentState(ReconciledPaymentState.Status.PENDING, Instant.now());
        } catch (Exception e) {
            log.warn("Stripe reconciliation lookup failed for reference (correlation_id present in MDC): {}", e.getMessage());
            return new ReconciledPaymentState(ReconciledPaymentState.Status.UNKNOWN, Instant.now());
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
