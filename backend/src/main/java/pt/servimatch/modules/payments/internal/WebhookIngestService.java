package pt.servimatch.modules.payments.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.billing.Subscription;
import pt.servimatch.modules.billing.SubscriptionLifecycle;
import pt.servimatch.modules.payments.GatewayEvent;
import pt.servimatch.modules.payments.PaymentGateway;
import pt.servimatch.platform.observability.CorrelationIdSupport;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestra a receção de um webhook de pagamento, na ordem exigida pela
 * skill {@code payment-webhook-hardening}:
 *
 * <ol>
 *   <li>identificar o evento (sem confiar nele) — {@link PaymentGateway#peekEventId};</li>
 *   <li>verificar a assinatura sobre os bytes exatos — {@link PaymentGateway#verifySignature};</li>
 *   <li>persistir o evento em bruto de forma idempotente
 *       ({@code UNIQUE(gateway, raw_event_id)}); duplicado → no-op, mesmo
 *       que a assinatura seja inválida em ambas as tentativas;</li>
 *   <li>só com assinatura válida <b>e</b> linha nova, aplicar ao domínio.</li>
 * </ol>
 *
 * <p>Nenhuma subscrição é ativada sem passar por este caminho a partir de
 * um evento com {@code signature_verified = true}.
 */
@Service
public class WebhookIngestService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestService.class);

    private final GatewayRegistry gatewayRegistry;
    private final JdbcPaymentGatewayEventRepository eventRepository;
    private final JdbcPaymentRepository paymentRepository;
    private final SubscriptionLifecycle subscriptionLifecycle;
    private final ObjectMapper objectMapper;

    public WebhookIngestService(GatewayRegistry gatewayRegistry,
                                 JdbcPaymentGatewayEventRepository eventRepository,
                                 JdbcPaymentRepository paymentRepository,
                                 SubscriptionLifecycle subscriptionLifecycle,
                                 ObjectMapper objectMapper) {
        this.gatewayRegistry = gatewayRegistry;
        this.eventRepository = eventRepository;
        this.paymentRepository = paymentRepository;
        this.subscriptionLifecycle = subscriptionLifecycle;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestOutcome ingest(String gatewayPathVariable, byte[] rawBody, Map<String, String> headers) {
        Optional<PaymentGateway> maybeGateway = gatewayRegistry.find(gatewayPathVariable);
        if (maybeGateway.isEmpty()) {
            return IngestOutcome.of(IngestOutcome.Result.UNKNOWN_GATEWAY);
        }
        PaymentGateway gateway = maybeGateway.get();

        String rawEventId = gateway.peekEventId(rawBody);
        boolean signatureValid = gateway.verifySignature(rawBody, headers);
        String payloadJson = safeJson(rawBody);

        Optional<UUID> insertedRowId = eventRepository.insertIfAbsent(
                gateway.code().value(), rawEventId, null, payloadJson, signatureValid);

        if (insertedRowId.isEmpty()) {
            log.info("Duplicate payment gateway event ignored (correlation_id={}, gateway={})",
                    CorrelationIdSupport.currentOrNull(), gateway.code().value());
            return IngestOutcome.of(IngestOutcome.Result.DUPLICATE);
        }

        if (!signatureValid) {
            log.warn("Payment webhook rejected: invalid signature (correlation_id={}, gateway={})",
                    CorrelationIdSupport.currentOrNull(), gateway.code().value());
            return IngestOutcome.of(IngestOutcome.Result.UNAUTHORIZED);
        }

        UUID eventRowId = insertedRowId.get();
        try {
            GatewayEvent event = gateway.parseEvent(rawBody, headers);
            applyToDomain(gateway, eventRowId, event);
            return IngestOutcome.of(IngestOutcome.Result.PROCESSED);
        } catch (IllegalArgumentException malformed) {
            eventRepository.markFailed(eventRowId, "malformed payload");
            return IngestOutcome.of(IngestOutcome.Result.BAD_REQUEST);
        } catch (RuntimeException unexpected) {
            // Nunca deixa uma falha de processamento (ex.: indisponibilidade
            // temporária da BD/rede) transformar-se em 500 sem rasto: fica
            // FAILED para o job de reconciliação retentar (ver ReconciliationJob).
            log.error("Payment webhook processing failed, will be retried by reconciliation (correlation_id={}, gateway={})",
                    CorrelationIdSupport.currentOrNull(), gateway.code().value(), unexpected);
            eventRepository.markFailed(eventRowId, "processing error");
            return IngestOutcome.of(IngestOutcome.Result.PROCESSED);
        }
    }

    /** Reaplica um evento já persistido e verificado, mas ainda não concluído (RECEIVED/FAILED) — usado pela reconciliação. */
    @Transactional
    public void reprocess(JdbcPaymentGatewayEventRepository.StoredEvent stored) {
        Optional<PaymentGateway> maybeGateway = gatewayRegistry.find(stored.gateway());
        if (maybeGateway.isEmpty() || !stored.signatureVerified()) {
            return;
        }
        try {
            byte[] rawBody = stored.payloadJson().getBytes(StandardCharsets.UTF_8);
            GatewayEvent event = maybeGateway.get().parseEvent(rawBody, Map.of());
            applyToDomain(maybeGateway.get(), stored.id(), event);
        } catch (RuntimeException e) {
            log.error("Reconciliation reprocessing failed (correlation_id={}, eventId={})",
                    CorrelationIdSupport.currentOrNull(), stored.id(), e);
            eventRepository.markFailed(stored.id(), "processing error");
        }
    }

    private void applyToDomain(PaymentGateway gateway, UUID eventRowId, GatewayEvent event) {
        switch (event) {
            case GatewayEvent.PaymentSucceeded succeeded -> handleSucceeded(gateway, eventRowId, succeeded);
            case GatewayEvent.PaymentFailed failed -> handleFailed(gateway, eventRowId, failed);
            case GatewayEvent.SubscriptionCanceledUpstream cancelled -> handleCancelled(gateway, eventRowId, cancelled);
            case GatewayEvent.Unrecognized unrecognized ->
                    eventRepository.markIgnored(eventRowId, "unrecognized event type: " + unrecognized.eventType());
        }
    }

    private void handleSucceeded(PaymentGateway gateway, UUID eventRowId, GatewayEvent.PaymentSucceeded event) {
        UUID subscriptionId = resolveSubscriptionId(gateway, event.subscriptionId(), event.gatewaySubscriptionId(), event.gatewayPaymentId());
        if (subscriptionId == null) {
            eventRepository.markIgnored(eventRowId, "no matching subscription");
            return;
        }
        Subscription subscription = subscriptionLifecycle.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            eventRepository.markIgnored(eventRowId, "subscription not found");
            return;
        }
        boolean stale = isStale(subscriptionId, event.occurredAt());
        paymentRepository.upsertResult(subscriptionId, subscription.providerId(), eventRowId, gateway.code().value(),
                event.gatewayPaymentId(), event.amountCents(), event.currency(), "SUCCEEDED", event.occurredAt());
        if (event.gatewaySubscriptionId().isPresent()) {
            subscriptionLifecycle.attachGatewayIds(subscriptionId, null, event.gatewaySubscriptionId().get());
        }
        if (!stale) {
            subscriptionLifecycle.activate(subscriptionId, event.periodStart(), event.periodEnd(), event.occurredAt());
        } else {
            log.info("Ignoring out-of-order PaymentSucceeded (older than already-applied event) (correlation_id={}, subscriptionId={})",
                    CorrelationIdSupport.currentOrNull(), subscriptionId);
        }
        eventRepository.markProcessed(eventRowId);
    }

    private void handleFailed(PaymentGateway gateway, UUID eventRowId, GatewayEvent.PaymentFailed event) {
        UUID subscriptionId = resolveSubscriptionId(gateway, event.subscriptionId(), event.gatewaySubscriptionId(), event.gatewayPaymentId());
        if (subscriptionId == null) {
            eventRepository.markIgnored(eventRowId, "no matching subscription");
            return;
        }
        Subscription subscription = subscriptionLifecycle.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            eventRepository.markIgnored(eventRowId, "subscription not found");
            return;
        }
        boolean stale = isStale(subscriptionId, event.occurredAt());
        paymentRepository.upsertResult(subscriptionId, subscription.providerId(), eventRowId, gateway.code().value(),
                event.gatewayPaymentId(), 0L, "EUR", "FAILED", event.occurredAt());
        if (!stale) {
            subscriptionLifecycle.markPastDue(subscriptionId, event.occurredAt());
        } else {
            log.info("Ignoring out-of-order PaymentFailed (older than already-applied event) (correlation_id={}, subscriptionId={})",
                    CorrelationIdSupport.currentOrNull(), subscriptionId);
        }
        eventRepository.markProcessed(eventRowId);
    }

    private void handleCancelled(PaymentGateway gateway, UUID eventRowId, GatewayEvent.SubscriptionCanceledUpstream event) {
        UUID subscriptionId = event.subscriptionId().orElseGet(() ->
                event.gatewaySubscriptionId()
                        .flatMap(gsi -> subscriptionLifecycle.findByGatewaySubscriptionId(gateway.code().value(), gsi))
                        .map(Subscription::id)
                        .orElse(null));
        if (subscriptionId == null) {
            eventRepository.markIgnored(eventRowId, "no matching subscription");
            return;
        }
        subscriptionLifecycle.cancel(subscriptionId, event.occurredAt());
        eventRepository.markProcessed(eventRowId);
    }

    private boolean isStale(UUID subscriptionId, Instant eventOccurredAt) {
        return paymentRepository.maxAppliedEventTime(subscriptionId)
                .map(maxApplied -> !eventOccurredAt.isAfter(maxApplied))
                .orElse(false);
    }

    private UUID resolveSubscriptionId(PaymentGateway gateway, Optional<UUID> direct,
                                        Optional<String> gatewaySubscriptionId, String gatewayPaymentId) {
        if (direct.isPresent()) {
            return direct.get();
        }
        if (gatewaySubscriptionId.isPresent()) {
            Optional<UUID> byGatewaySub = subscriptionLifecycle
                    .findByGatewaySubscriptionId(gateway.code().value(), gatewaySubscriptionId.get())
                    .map(Subscription::id);
            if (byGatewaySub.isPresent()) {
                return byGatewaySub.get();
            }
        }
        return paymentRepository.findSubscriptionIdByGatewayPaymentId(gateway.code().value(), gatewayPaymentId).orElse(null);
    }

    private String safeJson(byte[] rawBody) {
        try {
            objectMapper.readTree(rawBody);
            return new String(rawBody, StandardCharsets.UTF_8);
        } catch (Exception notJson) {
            try {
                return objectMapper.writeValueAsString(new String(rawBody, StandardCharsets.UTF_8));
            } catch (Exception e) {
                return "\"unreadable payload\"";
            }
        }
    }
}
