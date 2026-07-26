package pt.servimatch.modules.payments.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.servimatch.modules.billing.Subscription;
import pt.servimatch.modules.billing.SubscriptionLifecycle;
import pt.servimatch.modules.billing.SubscriptionStatus;
import pt.servimatch.modules.payments.GatewayCode;
import pt.servimatch.modules.payments.PaymentGateway;
import pt.servimatch.modules.payments.ReconciledPaymentState;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Job de reconciliação (ADR-0007, skill {@code payment-webhook-hardening}):
 * "webhooks perdem-se; a reconciliação é a rede de segurança, não um
 * extra". Duas responsabilidades distintas:
 *
 * <ol>
 *   <li><b>Retentar processamento pendente:</b> eventos já persistidos e
 *       com assinatura verificada, mas que falharam a aplicar ao domínio
 *       (ex.: indisponibilidade transitória da BD) — ver
 *       {@link WebhookIngestService#reprocess}.</li>
 *   <li><b>Comparar estado local vs. gateway:</b> para subscrições em
 *       {@code PENDING}/{@code ACTIVE}/{@code PAST_DUE}, consulta o
 *       gateway (fonte de verdade do pagamento) pela referência mais
 *       recente e corrige divergências — cobre a perda total do
 *       webhook, não apenas falhas de processamento já registadas.</li>
 * </ol>
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final JdbcPaymentGatewayEventRepository eventRepository;
    private final WebhookIngestService webhookIngestService;
    private final SubscriptionLifecycle subscriptionLifecycle;
    private final JdbcPaymentRepository paymentRepository;
    private final GatewayRegistry gatewayRegistry;

    public ReconciliationJob(JdbcPaymentGatewayEventRepository eventRepository,
                              WebhookIngestService webhookIngestService,
                              SubscriptionLifecycle subscriptionLifecycle,
                              JdbcPaymentRepository paymentRepository,
                              GatewayRegistry gatewayRegistry) {
        this.eventRepository = eventRepository;
        this.webhookIngestService = webhookIngestService;
        this.subscriptionLifecycle = subscriptionLifecycle;
        this.paymentRepository = paymentRepository;
        this.gatewayRegistry = gatewayRegistry;
    }

    @Scheduled(cron = "${SERVIMATCH_PAYMENTS_RECONCILIATION_CRON:0 */10 * * * *}")
    public void reconcile() {
        retryStuckEvents();
        reconcileAgainstGateway();
    }

    private void retryStuckEvents() {
        List<JdbcPaymentGatewayEventRepository.StoredEvent> stuck =
                eventRepository.findStaleUnprocessed(Instant.now().minus(Duration.ofMinutes(2)));
        for (JdbcPaymentGatewayEventRepository.StoredEvent event : stuck) {
            log.info("Reconciliation: retrying stuck payment gateway event (id={}, gateway={})", event.id(), event.gateway());
            webhookIngestService.reprocess(event);
        }
    }

    private void reconcileAgainstGateway() {
        for (SubscriptionStatus status : List.of(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE)) {
            for (Subscription subscription : subscriptionLifecycle.findByStatus(status)) {
                reconcileOne(subscription);
            }
        }
    }

    private void reconcileOne(Subscription subscription) {
        Optional<String> reference = paymentRepository.latestGatewayPaymentId(subscription.id());
        if (reference.isEmpty()) {
            return;
        }
        Optional<PaymentGateway> gateway = GatewayCode.fromValue(subscription.gateway()).flatMap(gatewayRegistry::find);
        if (gateway.isEmpty()) {
            return;
        }
        ReconciledPaymentState state = gateway.get().reconcile(reference.get());
        Instant now = Instant.now();
        switch (state.status()) {
            case PAID -> {
                if (subscription.status() != SubscriptionStatus.ACTIVE) {
                    log.warn("Reconciliation correcting divergence: gateway reports PAID, local status={}, subscriptionId={}",
                            subscription.status(), subscription.id());
                    subscriptionLifecycle.activate(subscription.id(),
                            subscription.currentPeriodStart() == null ? now : subscription.currentPeriodStart(),
                            subscription.currentPeriodEnd() == null ? now.plus(Duration.ofDays(30)) : subscription.currentPeriodEnd(),
                            now);
                }
            }
            case FAILED -> {
                if (subscription.status() == SubscriptionStatus.ACTIVE || subscription.status() == SubscriptionStatus.PENDING) {
                    log.warn("Reconciliation correcting divergence: gateway reports FAILED, local status={}, subscriptionId={}",
                            subscription.status(), subscription.id());
                    subscriptionLifecycle.markPastDue(subscription.id(), now);
                }
            }
            case CANCELED -> {
                if (!subscription.status().isTerminal()) {
                    log.warn("Reconciliation correcting divergence: gateway reports CANCELED, local status={}, subscriptionId={}",
                            subscription.status(), subscription.id());
                    subscriptionLifecycle.cancel(subscription.id(), now);
                }
            }
            case PENDING, UNKNOWN -> {
                // Nada a corrigir: ou ainda por pagar do lado do gateway, ou a
                // consulta falhou (não é sinal de divergência, é indisponibilidade).
            }
        }
    }
}
