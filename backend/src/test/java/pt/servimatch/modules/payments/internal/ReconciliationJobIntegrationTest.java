package pt.servimatch.modules.payments.internal;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import pt.servimatch.modules.billing.Subscription;
import pt.servimatch.modules.billing.SubscriptionStatus;
import pt.servimatch.modules.billing.internal.DefaultSubscriptionLifecycle;
import pt.servimatch.modules.billing.internal.JdbcSubscriptionPlanRepository;
import pt.servimatch.modules.billing.internal.JdbcSubscriptionRepository;
import pt.servimatch.modules.payments.CheckoutRequest;
import pt.servimatch.modules.payments.CheckoutResult;
import pt.servimatch.modules.payments.GatewayCode;
import pt.servimatch.modules.payments.GatewayEvent;
import pt.servimatch.modules.payments.PaymentGateway;
import pt.servimatch.modules.payments.ReconciledPaymentState;
import pt.servimatch.testsupport.TestDatabase;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "webhooks perdem-se; a reconciliação é a rede de segurança" — prova que
 * uma divergência introduzida artificialmente (estado local {@code PENDING},
 * gateway já reporta pago) é corrigida pelo job periódico, sem depender de
 * nenhum webhook. Usa um {@link PaymentGateway} de teste (não chama nenhum
 * gateway real) e Postgres real via {@link TestDatabase}.
 */
class ReconciliationJobIntegrationTest {

    private final JdbcClient jdbcClient = TestDatabase.jdbcClient();
    private final DefaultSubscriptionLifecycle lifecycle = new DefaultSubscriptionLifecycle(
            new JdbcSubscriptionPlanRepository(jdbcClient),
            new JdbcSubscriptionRepository(jdbcClient),
            event -> { });
    private final JdbcPaymentRepository paymentRepository = new JdbcPaymentRepository(jdbcClient);
    private final JdbcPaymentGatewayEventRepository eventRepository = new JdbcPaymentGatewayEventRepository(jdbcClient);

    @Test
    void correctsALocalPendingSubscriptionWhenGatewayAlreadyReportsPaid() {
        UUID providerId = TestDatabase.createProvider(jdbcClient);
        UUID planId = TestDatabase.createPlan(jdbcClient, 4500);
        Subscription subscription = lifecycle.createPending(providerId, planId, "eupago");
        paymentRepository.insertPending(subscription.id(), providerId, "eupago", subscription.id().toString(), 4500, "EUR", Instant.now());

        StubGateway stub = new StubGateway(GatewayCode.EUPAGO, ReconciledPaymentState.Status.PAID);
        GatewayRegistry registry = new GatewayRegistry(List.of(stub));
        WebhookIngestService ingestService = new WebhookIngestService(registry, eventRepository, paymentRepository, lifecycle, new com.fasterxml.jackson.databind.ObjectMapper());
        ReconciliationJob job = new ReconciliationJob(eventRepository, ingestService, lifecycle, paymentRepository, registry);

        // Pré-condição: a divergência existe (local PENDING, gateway já pago).
        assertThat(lifecycle.findById(subscription.id()).orElseThrow().status()).isEqualTo(SubscriptionStatus.PENDING);

        job.reconcile();

        Subscription reconciled = lifecycle.findById(subscription.id()).orElseThrow();
        assertThat(reconciled.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void correctsAnActiveSubscriptionWhenGatewayReportsCanceled() {
        UUID providerId = TestDatabase.createProvider(jdbcClient);
        UUID planId = TestDatabase.createPlan(jdbcClient, 4500);
        Subscription subscription = lifecycle.createPending(providerId, planId, "stripe");
        lifecycle.activate(subscription.id(), Instant.now(), Instant.now().plusSeconds(1000), Instant.now());
        paymentRepository.insertPending(subscription.id(), providerId, "stripe", "cs_reconcile_test", 4500, "EUR", Instant.now());

        StubGateway stub = new StubGateway(GatewayCode.STRIPE, ReconciledPaymentState.Status.CANCELED);
        GatewayRegistry registry = new GatewayRegistry(List.of(stub));
        WebhookIngestService ingestService = new WebhookIngestService(registry, eventRepository, paymentRepository, lifecycle, new com.fasterxml.jackson.databind.ObjectMapper());
        ReconciliationJob job = new ReconciliationJob(eventRepository, ingestService, lifecycle, paymentRepository, registry);

        job.reconcile();

        Subscription reconciled = lifecycle.findById(subscription.id()).orElseThrow();
        assertThat(reconciled.status()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    /** Dublê de teste: nunca contacta um gateway real, devolve sempre o mesmo estado configurado. */
    private record StubGateway(GatewayCode code, ReconciledPaymentState.Status status) implements PaymentGateway {

        @Override
        public CheckoutResult startCheckout(CheckoutRequest request) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public boolean verifySignature(byte[] rawBody, Map<String, String> headers) {
            return false;
        }

        @Override
        public String peekEventId(byte[] rawBody) {
            return "n/a";
        }

        @Override
        public GatewayEvent parseEvent(byte[] rawBody, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public ReconciledPaymentState reconcile(String correlationReference) {
            return new ReconciledPaymentState(status, Instant.now());
        }
    }
}
