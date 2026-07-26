package pt.servimatch.modules.payments.internal.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pt.servimatch.modules.payments.CheckoutRequest;
import pt.servimatch.modules.payments.CheckoutResult;
import pt.servimatch.modules.payments.GatewayEvent;
import pt.servimatch.modules.payments.internal.PaymentsProperties;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o adaptador Stripe sem chamar o Stripe real: {@link #startCheckout}
 * usa um servidor HTTP local (stub, {@link HttpServer} do JDK — sem
 * dependência adicional) em vez da API do Stripe; {@link #parseEvent} é
 * testado com payloads construídos localmente.
 */
class StripePaymentGatewayTest {

    private static HttpServer server;
    private static String baseUrl;

    @BeforeAll
    static void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/checkout/sessions", exchange -> {
            String response = "{\"id\":\"cs_test_123\",\"url\":\"https://checkout.example.test/cs_test_123\",\"customer\":\"cus_test_1\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    private StripePaymentGateway newGateway() {
        PaymentsProperties properties = new PaymentsProperties();
        properties.setStripeBaseUrl(baseUrl);
        properties.setStripeApiKey("sk_test_dummy");
        properties.setStripeWebhookSecret("whsec_test");
        properties.setStripeWebhookToleranceSeconds(300);
        return new StripePaymentGateway(RestClient.builder(), properties, new ObjectMapper());
    }

    @Test
    void startCheckoutCallsLocalStubAndParsesResponse() {
        StripePaymentGateway gateway = newGateway();
        UUID subscriptionId = UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(
                subscriptionId, UUID.randomUUID(), subscriptionId.toString(), 4500, "EUR", "Professional", "https://app.servimatch.pt/return");

        CheckoutResult result = gateway.startCheckout(request);

        assertThat(result.checkoutUrl()).isEqualTo("https://checkout.example.test/cs_test_123");
        assertThat(result.gatewayPaymentId()).isEqualTo("cs_test_123");
        assertThat(result.gatewayCustomerId()).isEqualTo("cus_test_1");
        assertThat(result.paymentReference()).isNull();
    }

    @Test
    void parsesCheckoutSessionCompletedIntoPaymentSucceeded() {
        StripePaymentGateway gateway = newGateway();
        UUID subscriptionId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String body = """
                {
                  "id": "evt_1",
                  "type": "checkout.session.completed",
                  "created": %d,
                  "data": { "object": {
                      "id": "cs_test_123",
                      "client_reference_id": "%s",
                      "subscription": "sub_abc",
                      "amount_total": 4500,
                      "currency": "eur"
                  } }
                }
                """.formatted(now, subscriptionId);

        GatewayEvent event = gateway.parseEvent(body.getBytes(StandardCharsets.UTF_8), Map.of());

        assertThat(event).isInstanceOf(GatewayEvent.PaymentSucceeded.class);
        GatewayEvent.PaymentSucceeded succeeded = (GatewayEvent.PaymentSucceeded) event;
        assertThat(succeeded.subscriptionId()).contains(subscriptionId);
        assertThat(succeeded.gatewaySubscriptionId()).contains("sub_abc");
        assertThat(succeeded.amountCents()).isEqualTo(4500);
        assertThat(succeeded.currency()).isEqualTo("EUR");
    }

    @Test
    void parsesInvoicePaymentFailedIntoPaymentFailed() {
        StripePaymentGateway gateway = newGateway();
        String body = """
                {
                  "id": "evt_2",
                  "type": "invoice.payment_failed",
                  "created": %d,
                  "data": { "object": { "id": "in_1", "subscription": "sub_abc" } }
                }
                """.formatted(Instant.now().getEpochSecond());

        GatewayEvent event = gateway.parseEvent(body.getBytes(StandardCharsets.UTF_8), Map.of());

        assertThat(event).isInstanceOf(GatewayEvent.PaymentFailed.class);
        assertThat(((GatewayEvent.PaymentFailed) event).gatewaySubscriptionId()).contains("sub_abc");
    }

    @Test
    void unknownEventTypeIsUnrecognized() {
        StripePaymentGateway gateway = newGateway();
        String body = """
                {"id": "evt_3", "type": "charge.dispute.created", "created": %d, "data": {"object": {}}}
                """.formatted(Instant.now().getEpochSecond());

        GatewayEvent event = gateway.parseEvent(body.getBytes(StandardCharsets.UTF_8), Map.of());

        assertThat(event).isInstanceOf(GatewayEvent.Unrecognized.class);
    }

    @Test
    void malformedPayloadThrowsIllegalArgument() {
        StripePaymentGateway gateway = newGateway();
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                        () -> gateway.parseEvent("not json at all {{{".getBytes(StandardCharsets.UTF_8), Map.of())))
                .isNotNull();
    }
}
