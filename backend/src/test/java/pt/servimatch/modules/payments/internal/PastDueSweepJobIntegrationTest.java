package pt.servimatch.modules.payments.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import pt.servimatch.modules.billing.Subscription;
import pt.servimatch.modules.billing.SubscriptionStatus;
import pt.servimatch.modules.billing.internal.DefaultSubscriptionLifecycle;
import pt.servimatch.modules.billing.internal.JdbcSubscriptionPlanRepository;
import pt.servimatch.modules.billing.internal.JdbcSubscriptionRepository;
import pt.servimatch.testsupport.TestDatabase;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Política de tolerância de {@code PAST_DUE} (CLAUDE.md: "estado de
 * tolerância com política explícita... não um limbo indefinido") contra
 * Postgres real. Duas formas independentes de esgotar a tolerância —
 * janela de tempo e número de tentativas — cada uma cancela por si só.
 */
class PastDueSweepJobIntegrationTest {

    private final JdbcClient jdbcClient = TestDatabase.jdbcClient();
    private final DefaultSubscriptionLifecycle lifecycle = new DefaultSubscriptionLifecycle(
            new JdbcSubscriptionPlanRepository(jdbcClient),
            new JdbcSubscriptionRepository(jdbcClient),
            event -> { });
    private final JdbcPaymentRepository paymentRepository = new JdbcPaymentRepository(jdbcClient);

    private Subscription pastDueSubscription(String gateway) {
        UUID providerId = TestDatabase.createProvider(jdbcClient);
        UUID planId = TestDatabase.createPlan(jdbcClient, 4500);
        Subscription subscription = lifecycle.createPending(providerId, planId, gateway);
        lifecycle.activate(subscription.id(), Instant.now(), Instant.now().plusSeconds(1000), Instant.now());
        lifecycle.markPastDue(subscription.id(), Instant.now());
        return subscription;
    }

    @Test
    void cancelsWhenGraceWindowIsExceeded() {
        Subscription subscription = pastDueSubscription("stripe");
        // Uma única falha, mas já fora da janela de tolerância (10 dias > 7 por omissão).
        paymentRepository.upsertResult(subscription.id(), subscription.providerId(), null, "stripe",
                "pi_grace_test", 0L, "EUR", "FAILED", Instant.now().minusSeconds(10L * 24 * 3600));

        PaymentsProperties properties = new PaymentsProperties();
        properties.setPastDueGraceDays(7);
        properties.setPastDueMaxAttempts(99); // isola o efeito da janela, não das tentativas
        new PastDueSweepJob(lifecycle, paymentRepository, properties).sweepPastDue();

        assertThat(lifecycle.findById(subscription.id()).orElseThrow().status()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void cancelsWhenMaxAttemptsIsExceededEvenWithinTheGraceWindow() {
        Subscription subscription = pastDueSubscription("stripe");
        for (int i = 0; i < 3; i++) {
            paymentRepository.upsertResult(subscription.id(), subscription.providerId(), null, "stripe",
                    "pi_attempts_test_" + i, 0L, "EUR", "FAILED", Instant.now().minusSeconds(60));
        }

        PaymentsProperties properties = new PaymentsProperties();
        properties.setPastDueGraceDays(30); // isola o efeito das tentativas, não da janela
        properties.setPastDueMaxAttempts(3);
        new PastDueSweepJob(lifecycle, paymentRepository, properties).sweepPastDue();

        assertThat(lifecycle.findById(subscription.id()).orElseThrow().status()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void staysInPastDueWithinGraceAndBelowAttemptLimit() {
        Subscription subscription = pastDueSubscription("stripe");
        paymentRepository.upsertResult(subscription.id(), subscription.providerId(), null, "stripe",
                "pi_ok_test", 0L, "EUR", "FAILED", Instant.now().minusSeconds(60));

        PaymentsProperties properties = new PaymentsProperties();
        properties.setPastDueGraceDays(7);
        properties.setPastDueMaxAttempts(3);
        new PastDueSweepJob(lifecycle, paymentRepository, properties).sweepPastDue();

        assertThat(lifecycle.findById(subscription.id()).orElseThrow().status()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }
}
