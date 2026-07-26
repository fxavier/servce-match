package pt.servimatch.modules.payments.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import pt.servimatch.modules.billing.Subscription;
import pt.servimatch.modules.billing.SubscriptionStatus;
import pt.servimatch.modules.billing.internal.DefaultSubscriptionLifecycle;
import pt.servimatch.modules.billing.internal.JdbcSubscriptionPlanRepository;
import pt.servimatch.modules.billing.internal.JdbcSubscriptionRepository;
import pt.servimatch.modules.payments.internal.stripe.StripePaymentGateway;
import pt.servimatch.testsupport.TestDatabase;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra Postgres real ({@link TestDatabase}), os critérios de
 * aceitação não negociáveis do módulo (CLAUDE.md, skill
 * {@code payment-webhook-hardening}): assinatura inválida rejeitada,
 * evento duplicado processado uma só vez, eventos fora de ordem não
 * corrompem o estado, pagamento falhado → {@code PAST_DUE}, corpo
 * malformado não gera 500. Usa o adaptador Stripe <b>real</b> (assinatura
 * HMAC de produção) com um segredo de teste — nunca contacta a Stripe real
 * (só {@link StripePaymentGateway#verifySignature}/{@code #parseEvent} são
 * exercitados, que não fazem I/O de rede).
 */
class WebhookIngestServiceIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_integration_test_secret";

    private final JdbcClient jdbcClient = TestDatabase.jdbcClient();
    private final DefaultSubscriptionLifecycle lifecycle = new DefaultSubscriptionLifecycle(
            new JdbcSubscriptionPlanRepository(jdbcClient),
            new JdbcSubscriptionRepository(jdbcClient),
            event -> { });
    private final JdbcPaymentRepository paymentRepository = new JdbcPaymentRepository(jdbcClient);
    private final JdbcPaymentGatewayEventRepository eventRepository = new JdbcPaymentGatewayEventRepository(jdbcClient);
    private final WebhookIngestService ingestService;
    private final StripePaymentGateway stripeGateway;

    WebhookIngestServiceIntegrationTest() {
        PaymentsProperties properties = new PaymentsProperties();
        properties.setStripeWebhookSecret(WEBHOOK_SECRET);
        properties.setStripeWebhookToleranceSeconds(300);
        properties.setStripeBaseUrl("http://localhost:1"); // nunca contactado nestes testes
        this.stripeGateway = new StripePaymentGateway(RestClient.builder(), properties, new ObjectMapper());
        this.ingestService = new WebhookIngestService(
                new GatewayRegistry(List.of(stripeGateway)), eventRepository, paymentRepository, lifecycle, new ObjectMapper());
    }

    private UUID createPendingSubscriptionWithPayment(String gatewayPaymentId) {
        UUID providerId = TestDatabase.createProvider(jdbcClient);
        UUID planId = TestDatabase.createPlan(jdbcClient, 4500);
        Subscription subscription = lifecycle.createPending(providerId, planId, "stripe");
        paymentRepository.insertPending(subscription.id(), providerId, "stripe", gatewayPaymentId, 4500, "EUR", Instant.now());
        return subscription.id();
    }

    private String checkoutCompletedBody(String eventId, UUID subscriptionId, long createdEpoch) {
        return """
                {
                  "id": "%s",
                  "type": "checkout.session.completed",
                  "created": %d,
                  "data": { "object": {
                      "id": "cs_test",
                      "client_reference_id": "%s",
                      "subscription": "sub_abc_%s",
                      "amount_total": 4500,
                      "currency": "eur"
                  } }
                }
                """.formatted(eventId, createdEpoch, subscriptionId, subscriptionId);
    }

    private String invoicePaymentFailedBody(String eventId, String stripeSubscriptionId, long createdEpoch) {
        return """
                {
                  "id": "%s",
                  "type": "invoice.payment_failed",
                  "created": %d,
                  "data": { "object": { "id": "in_%s", "subscription": "%s" } }
                }
                """.formatted(eventId, createdEpoch, eventId, stripeSubscriptionId);
    }

    private String invoicePaymentSucceededBody(String eventId, String stripeSubscriptionId, long createdEpoch) {
        return """
                {
                  "id": "%s",
                  "type": "invoice.payment_succeeded",
                  "created": %d,
                  "data": { "object": { "id": "in_%s", "subscription": "%s", "amount_paid": 4500, "currency": "eur",
                             "period_start": %d, "period_end": %d } }
                }
                """.formatted(eventId, createdEpoch, eventId, stripeSubscriptionId, createdEpoch, createdEpoch + 2592000);
    }

    private Map<String, String> signedHeaders(byte[] body) {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + new String(body, StandardCharsets.UTF_8);
        return Map.of("Stripe-Signature", "t=" + timestamp + ",v1=" + hmac(signedPayload));
    }

    private static String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void invalidSignatureIsRejectedAndHasNoDomainEffect() {
        UUID subscriptionId = createPendingSubscriptionWithPayment("cs_invalid_sig_test");
        byte[] body = checkoutCompletedBody("evt_invalid_" + UUID.randomUUID(), subscriptionId, Instant.now().getEpochSecond())
                .getBytes(StandardCharsets.UTF_8);
        Map<String, String> badHeaders = Map.of("Stripe-Signature", "t=" + Instant.now().getEpochSecond() + ",v1=deadbeef");

        IngestOutcome outcome = ingestService.ingest("stripe", body, badHeaders);

        assertThat(outcome.result()).isEqualTo(IngestOutcome.Result.UNAUTHORIZED);
        assertThat(lifecycle.findById(subscriptionId).orElseThrow().status()).isEqualTo(SubscriptionStatus.PENDING);
    }

    @Test
    void duplicateEventIsProcessedOnlyOnce() {
        UUID subscriptionId = createPendingSubscriptionWithPayment("cs_dup_test");
        String eventId = "evt_dup_" + UUID.randomUUID();
        byte[] body = checkoutCompletedBody(eventId, subscriptionId, Instant.now().getEpochSecond()).getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = signedHeaders(body);

        IngestOutcome first = ingestService.ingest("stripe", body, headers);
        IngestOutcome second = ingestService.ingest("stripe", body, headers);

        assertThat(first.result()).isEqualTo(IngestOutcome.Result.PROCESSED);
        assertThat(second.result()).isEqualTo(IngestOutcome.Result.DUPLICATE);
        // Garantia ao nível da BD, não em memória: UNIQUE(gateway, raw_event_id).
        assertThat(eventRepository.countByGatewayAndRawEventId("stripe", eventId)).isEqualTo(1);
        assertThat(lifecycle.findById(subscriptionId).orElseThrow().status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void paymentFailedMovesSubscriptionToPastDue() {
        UUID subscriptionId = createPendingSubscriptionWithPayment("cs_failed_test");
        long now = Instant.now().getEpochSecond();
        // Ativa primeiro (para simular uma renovação que falha depois de ativa).
        byte[] activationBody = checkoutCompletedBody("evt_activate_" + UUID.randomUUID(), subscriptionId, now).getBytes(StandardCharsets.UTF_8);
        ingestService.ingest("stripe", activationBody, signedHeaders(activationBody));
        assertThat(lifecycle.findById(subscriptionId).orElseThrow().status()).isEqualTo(SubscriptionStatus.ACTIVE);

        String stripeSubId = "sub_abc_" + subscriptionId;
        byte[] failedBody = invoicePaymentFailedBody("evt_failed_" + UUID.randomUUID(), stripeSubId, now + 10)
                .getBytes(StandardCharsets.UTF_8);

        IngestOutcome outcome = ingestService.ingest("stripe", failedBody, signedHeaders(failedBody));

        assertThat(outcome.result()).isEqualTo(IngestOutcome.Result.PROCESSED);
        assertThat(lifecycle.findById(subscriptionId).orElseThrow().status()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void outOfOrderEventsDoNotRegressState() {
        UUID subscriptionId = createPendingSubscriptionWithPayment("cs_ooo_test");
        long baseTime = Instant.now().getEpochSecond();
        String stripeSubId = "sub_abc_" + subscriptionId;

        // Ativa a subscrição (checkout completo).
        byte[] activation = checkoutCompletedBody("evt_ooo_activate_" + UUID.randomUUID(), subscriptionId, baseTime).getBytes(StandardCharsets.UTF_8);
        ingestService.ingest("stripe", activation, signedHeaders(activation));
        assertThat(lifecycle.findById(subscriptionId).orElseThrow().status()).isEqualTo(SubscriptionStatus.ACTIVE);

        // Renovação bem-sucedida mais recente (T+100).
        byte[] succeededLater = invoicePaymentSucceededBody("evt_ooo_succ_" + UUID.randomUUID(), stripeSubId, baseTime + 100)
                .getBytes(StandardCharsets.UTF_8);
        ingestService.ingest("stripe", succeededLater, signedHeaders(succeededLater));
        assertThat(lifecycle.findById(subscriptionId).orElseThrow().status()).isEqualTo(SubscriptionStatus.ACTIVE);

        // Um `payment_failed` chega DEPOIS mas com timestamp do gateway ANTERIOR (T+50) — entrega fora de ordem.
        byte[] failedEarlier = invoicePaymentFailedBody("evt_ooo_fail_" + UUID.randomUUID(), stripeSubId, baseTime + 50)
                .getBytes(StandardCharsets.UTF_8);
        IngestOutcome outcome = ingestService.ingest("stripe", failedEarlier, signedHeaders(failedEarlier));

        assertThat(outcome.result()).isEqualTo(IngestOutcome.Result.PROCESSED);
        // O evento antigo não pode regredir uma subscrição já ativada por um evento mais recente.
        assertThat(lifecycle.findById(subscriptionId).orElseThrow().status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void malformedBodyIsRejectedWithoutServerError() {
        byte[] garbage = "this is not json {{{".getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = signedHeaders(garbage);

        IngestOutcome outcome = ingestService.ingest("stripe", garbage, headers);

        assertThat(outcome.result()).isEqualTo(IngestOutcome.Result.BAD_REQUEST);
    }

    @Test
    void unknownGatewayIsRejected() {
        IngestOutcome outcome = ingestService.ingest("paypal", "{}".getBytes(StandardCharsets.UTF_8), Map.of());
        assertThat(outcome.result()).isEqualTo(IngestOutcome.Result.UNKNOWN_GATEWAY);
    }
}
