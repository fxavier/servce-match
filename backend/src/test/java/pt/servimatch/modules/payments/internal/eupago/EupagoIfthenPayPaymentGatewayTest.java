package pt.servimatch.modules.payments.internal.eupago;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pt.servimatch.modules.payments.CheckoutRequest;
import pt.servimatch.modules.payments.CheckoutResult;
import pt.servimatch.modules.payments.GatewayCode;
import pt.servimatch.modules.payments.GatewayEvent;
import pt.servimatch.modules.payments.ReconciledPaymentState;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Adaptador Eupago/IfthenPay contra um servidor HTTP local (JDK {@link HttpServer}) — nunca chama o fornecedor real. */
class EupagoIfthenPayPaymentGatewayTest {

    private static HttpServer server;
    private static String baseUrl;

    @BeforeAll
    static void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/multibanco/create", exchange -> respond(exchange, "{\"entity\":\"12345\",\"reference\":\"987654321\"}"));
        server.createContext("/multibanco/status", exchange -> respond(exchange, "{\"status\":\"paid\"}"));
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws java.io.IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    private EupagoIfthenPayPaymentGateway newGateway() {
        RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();
        return new EupagoIfthenPayPaymentGateway(GatewayCode.EUPAGO, restClient, "test-api-key", "test-webhook-secret", new ObjectMapper());
    }

    @Test
    void startCheckoutReturnsMultibancoReferenceFromLocalStub() {
        EupagoIfthenPayPaymentGateway gateway = newGateway();
        UUID subscriptionId = UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(
                subscriptionId, UUID.randomUUID(), subscriptionId.toString(), 4500, "EUR", "Professional", "https://app.servimatch.pt/return");

        CheckoutResult result = gateway.startCheckout(request);

        assertThat(result.checkoutUrl()).isNull();
        assertThat(result.paymentReference()).isNotNull();
        assertThat(result.paymentReference().entity()).isEqualTo("12345");
        assertThat(result.paymentReference().reference()).isEqualTo("987654321");
        assertThat(result.paymentReference().amountCents()).isEqualTo(4500);
        assertThat(result.gatewayPaymentId()).isEqualTo(subscriptionId.toString());
    }

    @Test
    void reconcileReadsStatusFromLocalStub() {
        EupagoIfthenPayPaymentGateway gateway = newGateway();
        ReconciledPaymentState state = gateway.reconcile("some-reference");
        assertThat(state.status()).isEqualTo(ReconciledPaymentState.Status.PAID);
    }

    @Test
    void parsesPaidCallbackIntoPaymentSucceeded() {
        EupagoIfthenPayPaymentGateway gateway = newGateway();
        UUID subscriptionId = UUID.randomUUID();
        String body = """
                {
                  "identifier": "%s",
                  "reference": "987654321",
                  "amount": "45.00",
                  "currency": "EUR",
                  "status": "paid",
                  "dateTime": "2026-07-25T10:00:00Z"
                }
                """.formatted(subscriptionId);

        GatewayEvent event = gateway.parseEvent(body.getBytes(StandardCharsets.UTF_8), Map.of());

        assertThat(event).isInstanceOf(GatewayEvent.PaymentSucceeded.class);
        GatewayEvent.PaymentSucceeded succeeded = (GatewayEvent.PaymentSucceeded) event;
        assertThat(succeeded.subscriptionId()).contains(subscriptionId);
        assertThat(succeeded.amountCents()).isEqualTo(4500);
    }

    @Test
    void parsesFailedCallbackIntoPaymentFailed() {
        EupagoIfthenPayPaymentGateway gateway = newGateway();
        UUID subscriptionId = UUID.randomUUID();
        String body = """
                {"identifier": "%s", "reference": "1", "status": "expired", "dateTime": "2026-07-25T10:00:00Z"}
                """.formatted(subscriptionId);

        GatewayEvent event = gateway.parseEvent(body.getBytes(StandardCharsets.UTF_8), Map.of());

        assertThat(event).isInstanceOf(GatewayEvent.PaymentFailed.class);
    }

    @Test
    void malformedPayloadThrowsIllegalArgument() {
        EupagoIfthenPayPaymentGateway gateway = newGateway();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> gateway.parseEvent("{{{not json".getBytes(StandardCharsets.UTF_8), Map.of()));
    }
}
