package pt.servimatch.modules.payments.internal;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
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
 *
 * <p><b>Porquê uma pool aqui e não {@link TestDatabase#jdbcClient()}
 * diretamente:</b> {@link ReconciliationJob#reconcile()} varre, de
 * propósito, <em>todas</em> as subscrições {@code PENDING}/{@code ACTIVE}/
 * {@code PAST_DUE} do contentor Postgres partilhado (é a cobertura real que
 * este teste prova — não se restringe à subscrição criada aqui). Esse
 * contentor é partilhado por toda a JVM de testes e nunca é limpo entre
 * classes ({@code SharedPostgis}), incluindo dezenas de milhares de
 * subscrições semeadas por {@code MatchingEligibilityTest} para exercitar o
 * planeador do PostGIS. {@link TestDatabase#dataSource()} é um
 * {@code PGSimpleDataSource} sem pooling: cada uma dessas linhas
 * varridas custava uma ligação TCP nova através do encaminhamento de porta
 * do Docker — o multiplicador exato que tornava este teste (2 asserções)
 * o gargalo de toda a suíte. Reutilizar ligações via uma pool pequena e
 * local a este ficheiro elimina esse custo sem reduzir o volume varrido
 * nem o que é provado.
 */
class ReconciliationJobIntegrationTest {

    private final HikariDataSource pooledDataSource = pooledDataSource();
    private final JdbcClient jdbcClient = JdbcClient.create(pooledDataSource);
    private final DefaultSubscriptionLifecycle lifecycle = new DefaultSubscriptionLifecycle(
            new JdbcSubscriptionPlanRepository(jdbcClient),
            new JdbcSubscriptionRepository(jdbcClient),
            event -> { });
    private final JdbcPaymentRepository paymentRepository = new JdbcPaymentRepository(jdbcClient);
    private final JdbcPaymentGatewayEventRepository eventRepository = new JdbcPaymentGatewayEventRepository(jdbcClient);

    private static HikariDataSource pooledDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDataSource(TestDatabase.dataSource());
        dataSource.setPoolName("ReconciliationJobIntegrationTest");
        dataSource.setMaximumPoolSize(4);
        return dataSource;
    }

    @AfterEach
    void closePool() {
        pooledDataSource.close();
    }

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
