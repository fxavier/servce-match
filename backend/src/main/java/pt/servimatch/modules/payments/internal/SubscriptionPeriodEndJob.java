package pt.servimatch.modules.payments.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.servimatch.modules.billing.Subscription;
import pt.servimatch.modules.billing.SubscriptionLifecycle;
import pt.servimatch.modules.payments.GatewayFamily;

import java.time.Instant;

/**
 * Job de expiração (ARQUITETURA §12.1): varre {@code current_period_end < now}
 * para subscrições {@code ACTIVE} sem renovação confirmada por webhook —
 * a rede de segurança para quando o gateway nunca chega a notificar.
 *
 * <p><b>Não trata todos os métodos da mesma forma</b> (ADR-0007 §12.2, o
 * erro estrutural mais provável deste módulo): cartão (Stripe) tem
 * dunning nativo, pelo que aqui só entra em {@code PAST_DUE} (as
 * tentativas de cobrança continuam do lado do Stripe, conduzidas por
 * webhook); Multibanco/MB WAY (Eupago/IfthenPay) são *invoice-based* — sem
 * referência paga a tempo, não há "tentativa automática" nenhuma para
 * esperar, a subscrição expira diretamente.
 */
@Component
public class SubscriptionPeriodEndJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPeriodEndJob.class);

    private final SubscriptionLifecycle subscriptionLifecycle;

    public SubscriptionPeriodEndJob(SubscriptionLifecycle subscriptionLifecycle) {
        this.subscriptionLifecycle = subscriptionLifecycle;
    }

    @Scheduled(cron = "${SERVIMATCH_BILLING_PERIOD_END_CRON:0 */15 * * * *}")
    public void sweepPeriodEnds() {
        Instant now = Instant.now();
        for (Subscription subscription : subscriptionLifecycle.findActiveSubscriptionsPastPeriodEnd(now)) {
            if (GatewayFamily.isInvoiceBased(subscription.gateway())) {
                subscriptionLifecycle.expire(subscription.id(), now);
                log.info("Subscription expired at period end (invoice-based gateway, no payment): subscriptionId={}", subscription.id());
            } else {
                subscriptionLifecycle.markPastDue(subscription.id(), now);
                log.info("Subscription entered PAST_DUE at period end (recurring gateway dunning continues via webhook): subscriptionId={}", subscription.id());
            }
        }
    }
}
