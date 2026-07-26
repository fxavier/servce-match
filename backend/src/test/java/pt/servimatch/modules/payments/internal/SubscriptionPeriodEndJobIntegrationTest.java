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
 * ADR-0007 §"Recorrência por método de pagamento": ao fim do período sem
 * renovação confirmada, cartão (Stripe, auto-renew/dunning nativo) e
 * Multibanco/MB WAY (Eupago/IfthenPay, *invoice-based*, sem débito
 * automático) <b>não podem</b> ser tratados da mesma forma — é o erro
 * estrutural mais provável deste módulo. Prova contra Postgres real que
 * o job de expiração aplica a política certa a cada um.
 */
class SubscriptionPeriodEndJobIntegrationTest {

    private final JdbcClient jdbcClient = TestDatabase.jdbcClient();
    private final DefaultSubscriptionLifecycle lifecycle = new DefaultSubscriptionLifecycle(
            new JdbcSubscriptionPlanRepository(jdbcClient),
            new JdbcSubscriptionRepository(jdbcClient),
            event -> { });

    private Subscription activeSubscriptionPastPeriodEnd(String gateway) {
        UUID providerId = TestDatabase.createProvider(jdbcClient);
        UUID planId = TestDatabase.createPlan(jdbcClient, 4500);
        Subscription subscription = lifecycle.createPending(providerId, planId, gateway);
        Instant pastPeriodEnd = Instant.now().minusSeconds(3600);
        lifecycle.activate(subscription.id(), pastPeriodEnd.minusSeconds(2592000), pastPeriodEnd, Instant.now());
        return subscription;
    }

    @Test
    void cardSubscriptionPastPeriodEndEntersPastDueNotExpired() {
        Subscription subscription = activeSubscriptionPastPeriodEnd("stripe");

        new SubscriptionPeriodEndJob(lifecycle).sweepPeriodEnds();

        assertThat(lifecycle.findById(subscription.id()).orElseThrow().status()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void multibancoSubscriptionPastPeriodEndExpiresDirectlyWithoutPastDue() {
        Subscription subscription = activeSubscriptionPastPeriodEnd("eupago");

        new SubscriptionPeriodEndJob(lifecycle).sweepPeriodEnds();

        assertThat(lifecycle.findById(subscription.id()).orElseThrow().status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void ifthenpaySubscriptionPastPeriodEndAlsoExpiresDirectly() {
        Subscription subscription = activeSubscriptionPastPeriodEnd("ifthenpay");

        new SubscriptionPeriodEndJob(lifecycle).sweepPeriodEnds();

        assertThat(lifecycle.findById(subscription.id()).orElseThrow().status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void subscriptionStillWithinPeriodIsUntouched() {
        UUID providerId = TestDatabase.createProvider(jdbcClient);
        UUID planId = TestDatabase.createPlan(jdbcClient, 4500);
        Subscription subscription = lifecycle.createPending(providerId, planId, "stripe");
        lifecycle.activate(subscription.id(), Instant.now(), Instant.now().plusSeconds(2592000), Instant.now());

        new SubscriptionPeriodEndJob(lifecycle).sweepPeriodEnds();

        assertThat(lifecycle.findById(subscription.id()).orElseThrow().status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }
}
