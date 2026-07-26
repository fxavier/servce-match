package pt.servimatch.modules.billing;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import pt.servimatch.modules.billing.internal.DefaultSubscriptionLifecycle;
import pt.servimatch.modules.billing.internal.JdbcSubscriptionPlanRepository;
import pt.servimatch.modules.billing.internal.JdbcSubscriptionRepository;
import pt.servimatch.testsupport.TestDatabase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ciclo de vida da subscrição (ARQUITETURA §12.1) contra Postgres real
 * ({@link TestDatabase}) — sem contentor Spring: {@link DefaultSubscriptionLifecycle}
 * é construído diretamente com um {@link JdbcClient} real, o que já basta
 * para exercitar as escritas condicionais em SQL.
 */
class SubscriptionLifecycleStateMachineTest {

    private final JdbcClient jdbcClient = TestDatabase.jdbcClient();
    private final List<Object> publishedEvents = new ArrayList<>();
    private final DefaultSubscriptionLifecycle lifecycle = new DefaultSubscriptionLifecycle(
            new JdbcSubscriptionPlanRepository(jdbcClient),
            new JdbcSubscriptionRepository(jdbcClient),
            (ApplicationEventPublisher) publishedEvents::add);

    private UUID providerId() {
        return TestDatabase.createProvider(jdbcClient);
    }

    private UUID planId() {
        return TestDatabase.createPlan(jdbcClient, 4500);
    }

    @Test
    void newSubscriptionStartsPending() {
        Subscription subscription = lifecycle.createPending(providerId(), planId(), "stripe");
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.PENDING);
    }

    @Test
    void activatingAPendingSubscriptionMakesItActiveAndPublishesEvent() {
        Subscription subscription = lifecycle.createPending(providerId(), planId(), "stripe");
        Instant start = Instant.now();
        Instant end = start.plusSeconds(30L * 24 * 3600);

        Subscription activated = lifecycle.activate(subscription.id(), start, end, Instant.now());

        assertThat(activated.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(pt.servimatch.modules.billing.events.SubscriptionActivated.class);
    }

    @Test
    void failedPaymentMovesActiveSubscriptionToPastDue() {
        Subscription subscription = lifecycle.createPending(providerId(), planId(), "stripe");
        lifecycle.activate(subscription.id(), Instant.now(), Instant.now().plusSeconds(1000), Instant.now());

        Subscription pastDue = lifecycle.markPastDue(subscription.id(), Instant.now());

        assertThat(pastDue.status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(pastDue.status().grantsVisibility()).isTrue();
    }

    @Test
    void pastDueSubscriptionCanRecoverToActive() {
        Subscription subscription = lifecycle.createPending(providerId(), planId(), "stripe");
        lifecycle.activate(subscription.id(), Instant.now(), Instant.now().plusSeconds(1000), Instant.now());
        lifecycle.markPastDue(subscription.id(), Instant.now());

        Subscription recovered = lifecycle.activate(subscription.id(), Instant.now(), Instant.now().plusSeconds(2000), Instant.now());

        assertThat(recovered.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void terminalStatesNeverTransitionAgain_cancelled() {
        Subscription subscription = lifecycle.createPending(providerId(), planId(), "stripe");
        lifecycle.activate(subscription.id(), Instant.now(), Instant.now().plusSeconds(1000), Instant.now());
        Subscription cancelled = lifecycle.cancel(subscription.id(), Instant.now());
        assertThat(cancelled.status()).isEqualTo(SubscriptionStatus.CANCELLED);

        // Um evento de pagamento tardio (ex.: webhook fora de ordem) nunca ressuscita uma subscrição cancelada.
        Subscription afterLateEvent = lifecycle.activate(subscription.id(), Instant.now(), Instant.now().plusSeconds(2000), Instant.now());

        assertThat(afterLateEvent.status()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void terminalStatesNeverTransitionAgain_expired() {
        Subscription subscription = lifecycle.createPending(providerId(), planId(), "stripe");
        lifecycle.activate(subscription.id(), Instant.now(), Instant.now().plusSeconds(1000), Instant.now());
        Subscription expired = lifecycle.expire(subscription.id(), Instant.now());
        assertThat(expired.status()).isEqualTo(SubscriptionStatus.EXPIRED);

        Subscription afterLateEvent = lifecycle.markPastDue(subscription.id(), Instant.now());

        assertThat(afterLateEvent.status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void aProviderCannotHaveTwoNonTerminalSubscriptions() {
        UUID provider = providerId();
        UUID plan = planId();
        lifecycle.createPending(provider, plan, "stripe");

        assertThatThrownBy(() -> lifecycle.createPending(provider, plan, "eupago"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void gatingReflectsCurrentStatus() {
        UUID provider = providerId();
        Subscription subscription = lifecycle.createPending(provider, planId(), "stripe");
        assertThat(lifecycle.hasActiveSubscription(provider)).isFalse();
        assertThat(lifecycle.isVisibilityEligible(provider)).isFalse();

        lifecycle.activate(subscription.id(), Instant.now(), Instant.now().plusSeconds(1000), Instant.now());
        assertThat(lifecycle.hasActiveSubscription(provider)).isTrue();
        assertThat(lifecycle.isVisibilityEligible(provider)).isTrue();

        lifecycle.markPastDue(subscription.id(), Instant.now());
        assertThat(lifecycle.hasActiveSubscription(provider)).isFalse();
        assertThat(lifecycle.isVisibilityEligible(provider)).isTrue();
    }
}
