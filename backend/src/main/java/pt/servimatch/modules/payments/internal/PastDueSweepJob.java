package pt.servimatch.modules.payments.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.servimatch.modules.billing.Subscription;
import pt.servimatch.modules.billing.SubscriptionLifecycle;

import java.time.Duration;
import java.time.Instant;

/**
 * Política de tolerância de {@code PAST_DUE} (CLAUDE.md: "estado de
 * tolerância com política explícita, não um limbo indefinido"). Janela e
 * número de tentativas configuráveis via {@link PaymentsProperties}
 * ({@code SERVIMATCH_BILLING_PAST_DUE_GRACE_DAYS}/
 * {@code SERVIMATCH_BILLING_PAST_DUE_MAX_ATTEMPTS}). "Tentativas" contam
 * pagamentos {@code FAILED} desde o último {@code SUCCEEDED} — ver
 * {@code JdbcPaymentRepository#failureStreak}.
 */
@Component
public class PastDueSweepJob {

    private static final Logger log = LoggerFactory.getLogger(PastDueSweepJob.class);

    private final SubscriptionLifecycle subscriptionLifecycle;
    private final JdbcPaymentRepository paymentRepository;
    private final PaymentsProperties properties;

    public PastDueSweepJob(SubscriptionLifecycle subscriptionLifecycle, JdbcPaymentRepository paymentRepository, PaymentsProperties properties) {
        this.subscriptionLifecycle = subscriptionLifecycle;
        this.paymentRepository = paymentRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "${SERVIMATCH_BILLING_PAST_DUE_CRON:0 */30 * * * *}")
    public void sweepPastDue() {
        Instant now = Instant.now();
        Duration grace = Duration.ofDays(properties.pastDueGraceDays());
        for (Subscription subscription : subscriptionLifecycle.findPastDueSubscriptions()) {
            JdbcPaymentRepository.FailureStreak streak = paymentRepository.failureStreak(subscription.id());
            boolean graceExceeded = streak.since() != null && Duration.between(streak.since(), now).compareTo(grace) > 0;
            boolean attemptsExceeded = streak.attempts() >= properties.pastDueMaxAttempts();
            if (graceExceeded || attemptsExceeded) {
                subscriptionLifecycle.cancel(subscription.id(), now);
                log.info("Subscription cancelled after PAST_DUE window/attempts exhausted: subscriptionId={}, attempts={}, since={}",
                        subscription.id(), streak.attempts(), streak.since());
            }
        }
    }
}
