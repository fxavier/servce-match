package pt.servimatch.modules.billing.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.billing.Subscription;
import pt.servimatch.modules.billing.SubscriptionLifecycle;
import pt.servimatch.modules.billing.SubscriptionPlan;
import pt.servimatch.modules.billing.SubscriptionStatus;
import pt.servimatch.modules.billing.SubscriptionVisibilitySql;
import pt.servimatch.modules.billing.events.SubscriptionActivated;
import pt.servimatch.modules.billing.events.SubscriptionCancelled;
import pt.servimatch.modules.billing.events.SubscriptionExpired;
import pt.servimatch.modules.billing.events.SubscriptionPastDue;
import pt.servimatch.platform.observability.CorrelationIdSupport;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação de referência de {@link SubscriptionLifecycle}. Cada
 * transição é uma escrita condicional ({@code WHERE status IN (...)}) —
 * garante o invariante de estado terminal mesmo sob chamadas concorrentes
 * ou reentregas, sem depender de verificação prévia em memória.
 *
 * <p>Este bean não precisa de tratamento especial para contextos sem base
 * de dados: depende só de {@link JdbcSubscriptionPlanRepository}/
 * {@link JdbcSubscriptionRepository}, que existem sempre (ver o javadoc
 * de {@link JdbcSubscriptionPlanRepository} sobre {@code Optional<JdbcClient>}).
 */
@Service
public class DefaultSubscriptionLifecycle implements SubscriptionLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionLifecycle.class);

    private final JdbcSubscriptionPlanRepository planRepository;
    private final JdbcSubscriptionRepository subscriptionRepository;
    private final ApplicationEventPublisher events;

    public DefaultSubscriptionLifecycle(JdbcSubscriptionPlanRepository planRepository,
                                         JdbcSubscriptionRepository subscriptionRepository,
                                         ApplicationEventPublisher events) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.events = events;
    }

    @Override
    public List<SubscriptionPlan> listActivePlans() {
        return planRepository.findActive();
    }

    @Override
    public Optional<SubscriptionPlan> findPlan(UUID planId) {
        return planRepository.findById(planId);
    }

    @Override
    public Optional<Subscription> findById(UUID subscriptionId) {
        return subscriptionRepository.findById(subscriptionId);
    }

    @Override
    public Optional<Subscription> findByProvider(UUID providerId) {
        return subscriptionRepository.findNonTerminalByProvider(providerId);
    }

    @Override
    public Optional<Subscription> findMostRecentByProvider(UUID providerId) {
        return subscriptionRepository.findMostRecentByProvider(providerId);
    }

    @Override
    public Optional<Subscription> findByGatewaySubscriptionId(String gateway, String gatewaySubscriptionId) {
        return subscriptionRepository.findByGatewaySubscriptionId(gateway, gatewaySubscriptionId);
    }

    @Override
    @Transactional
    public Subscription createPending(UUID providerId, UUID planId, String gateway) {
        Subscription subscription = subscriptionRepository.insertPending(providerId, planId, gateway);
        log.info("Subscription created as PENDING (correlation_id={}, subscriptionId={}, gateway={})",
                CorrelationIdSupport.currentOrNull(), subscription.id(), gateway);
        return subscription;
    }

    @Override
    @Transactional
    public Subscription attachGatewayIds(UUID subscriptionId, String gatewayCustomerId, String gatewaySubscriptionId) {
        return subscriptionRepository.attachGatewayIds(subscriptionId, gatewayCustomerId, gatewaySubscriptionId);
    }

    @Override
    @Transactional
    public Subscription activate(UUID subscriptionId, Instant periodStart, Instant periodEnd, Instant eventOccurredAt) {
        boolean applied = subscriptionRepository.transition(
                subscriptionId,
                List.of(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE),
                SubscriptionStatus.ACTIVE,
                periodStart, periodEnd, null);
        Subscription current = subscriptionRepository.findById(subscriptionId).orElseThrow();
        if (applied) {
            events.publishEvent(new SubscriptionActivated(
                    current.id(), current.providerId(), current.planId(),
                    current.currentPeriodStart(), current.currentPeriodEnd(), eventOccurredAt));
        } else {
            log.info("Activation ignored: subscription already terminal or not found (correlation_id={}, subscriptionId={})",
                    CorrelationIdSupport.currentOrNull(), subscriptionId);
        }
        return current;
    }

    @Override
    @Transactional
    public Subscription markPastDue(UUID subscriptionId, Instant eventOccurredAt) {
        boolean applied = subscriptionRepository.transition(
                subscriptionId,
                List.of(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE),
                SubscriptionStatus.PAST_DUE,
                null, null, null);
        Subscription current = subscriptionRepository.findById(subscriptionId).orElseThrow();
        if (applied) {
            events.publishEvent(new SubscriptionPastDue(current.id(), current.providerId(), eventOccurredAt));
        }
        return current;
    }

    @Override
    @Transactional
    public Subscription expire(UUID subscriptionId, Instant eventOccurredAt) {
        boolean applied = subscriptionRepository.transition(
                subscriptionId,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PENDING),
                SubscriptionStatus.EXPIRED,
                null, null, null);
        Subscription current = subscriptionRepository.findById(subscriptionId).orElseThrow();
        if (applied) {
            events.publishEvent(new SubscriptionExpired(current.id(), current.providerId(), eventOccurredAt));
        }
        return current;
    }

    @Override
    @Transactional
    public Subscription cancel(UUID subscriptionId, Instant eventOccurredAt) {
        boolean applied = subscriptionRepository.transition(
                subscriptionId,
                List.of(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE),
                SubscriptionStatus.CANCELLED,
                null, null, null);
        Subscription current = subscriptionRepository.findById(subscriptionId).orElseThrow();
        if (applied) {
            events.publishEvent(new SubscriptionCancelled(current.id(), current.providerId(), eventOccurredAt));
        }
        return current;
    }

    @Override
    public boolean hasActiveSubscription(UUID providerId) {
        return subscriptionRepository.findNonTerminalByProvider(providerId)
                .map(s -> s.status() == SubscriptionStatus.ACTIVE)
                .orElse(false);
    }

    @Override
    public boolean isVisibilityEligible(UUID providerId) {
        return subscriptionRepository.isVisibilityEligible(
                providerId, SubscriptionVisibilitySql.DEFAULT_PERIOD_END_TOLERANCE_SECONDS);
    }

    @Override
    public List<Subscription> findActiveSubscriptionsPastPeriodEnd(Instant now) {
        return subscriptionRepository.findExpiredCandidates(now);
    }

    @Override
    public List<Subscription> findPastDueSubscriptions() {
        return subscriptionRepository.findPastDueCandidates();
    }

    @Override
    public List<Subscription> findByStatus(SubscriptionStatus status) {
        return subscriptionRepository.findByStatus(status);
    }
}
