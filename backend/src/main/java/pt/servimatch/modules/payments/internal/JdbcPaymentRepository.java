package pt.servimatch.modules.payments.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência de {@code payment} (V12). {@code created_at} é
 * deliberadamente definido como o instante do evento <b>do lado do
 * gateway</b> (não o instante de inserção) — é o que permite ao
 * processador detetar eventos fora de ordem por comparação simples de
 * {@code MAX(created_at)} por subscrição, sem precisar de uma coluna nova
 * no schema (skill {@code payment-webhook-hardening}: "compara
 * timestamp/versão do evento com o estado atual").
 */
@Repository
public class JdbcPaymentRepository {

    private final JdbcClient jdbcClient;

    public JdbcPaymentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insertPending(UUID subscriptionId, UUID providerId, String gateway, String gatewayPaymentId,
                               long amountCents, String currency, Instant createdAt) {
        jdbcClient.sql("""
                        INSERT INTO payment (id, subscription_id, provider_id, gateway, gateway_payment_id, amount_cents, currency, status, created_at, updated_at)
                        VALUES (:id, :subscriptionId, :providerId, :gateway, :gatewayPaymentId, :amountCents, :currency, 'PENDING', :createdAt, :createdAt)
                        ON CONFLICT (gateway, gateway_payment_id) DO NOTHING
                        """)
                .param("id", UUID.randomUUID())
                .param("subscriptionId", subscriptionId)
                .param("providerId", providerId)
                .param("gateway", gateway)
                .param("gatewayPaymentId", gatewayPaymentId)
                .param("amountCents", amountCents)
                .param("currency", currency)
                .param("createdAt", Timestamp.from(createdAt))
                .update();
    }

    /**
     * Regista (ou atualiza) o resultado de um pagamento. Se
     * {@code (gateway, gatewayPaymentId)} já existir (ex.: a linha
     * {@code PENDING} criada no checkout), atualiza o estado sem tocar em
     * {@code created_at} — preserva o instante original do evento. Se não
     * existir (ex.: fatura de renovação automática Stripe, sem checkout
     * nosso prévio), insere de novo com {@code created_at = occurredAt}.
     */
    public void upsertResult(UUID subscriptionId, UUID providerId, UUID gatewayEventId, String gateway,
                              String gatewayPaymentId, long amountCents, String currency, String status, Instant occurredAt) {
        jdbcClient.sql("""
                        INSERT INTO payment (id, subscription_id, provider_id, gateway_event_id, gateway, gateway_payment_id, amount_cents, currency, status, created_at, updated_at)
                        VALUES (:id, :subscriptionId, :providerId, :gatewayEventId, :gateway, :gatewayPaymentId, :amountCents, :currency, :status, :occurredAt, :occurredAt)
                        ON CONFLICT (gateway, gateway_payment_id) DO UPDATE
                            SET status = EXCLUDED.status,
                                subscription_id = COALESCE(payment.subscription_id, EXCLUDED.subscription_id),
                                gateway_event_id = EXCLUDED.gateway_event_id,
                                updated_at = now()
                        """)
                .param("id", UUID.randomUUID())
                .param("subscriptionId", subscriptionId)
                .param("providerId", providerId)
                .param("gatewayEventId", gatewayEventId)
                .param("gateway", gateway)
                .param("gatewayPaymentId", gatewayPaymentId)
                .param("amountCents", amountCents)
                .param("currency", currency)
                .param("status", status)
                .param("occurredAt", Timestamp.from(occurredAt))
                .update();
    }

    /** Maior {@code created_at} (instante do evento no gateway) já aplicado a esta subscrição — guarda contra desordem. */
    public Optional<Instant> maxAppliedEventTime(UUID subscriptionId) {
        Timestamp max = jdbcClient.sql("""
                        SELECT max(created_at) FROM payment
                        WHERE subscription_id = :subscriptionId AND status IN ('SUCCEEDED','FAILED')
                        """)
                .param("subscriptionId", subscriptionId)
                .query(Timestamp.class)
                .optional()
                .orElse(null);
        return Optional.ofNullable(max).map(Timestamp::toInstant);
    }

    /** Referência de pagamento mais recente da subscrição — usada pela reconciliação para consultar o gateway. */
    public Optional<String> latestGatewayPaymentId(UUID subscriptionId) {
        return jdbcClient.sql("""
                        SELECT gateway_payment_id FROM payment
                        WHERE subscription_id = :subscriptionId
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .param("subscriptionId", subscriptionId)
                .query(String.class)
                .optional();
    }

    public Optional<UUID> findSubscriptionIdByGatewayPaymentId(String gateway, String gatewayPaymentId) {
        return jdbcClient.sql("SELECT subscription_id FROM payment WHERE gateway = :gateway AND gateway_payment_id = :gatewayPaymentId AND subscription_id IS NOT NULL")
                .param("gateway", gateway)
                .param("gatewayPaymentId", gatewayPaymentId)
                .query(UUID.class)
                .optional();
    }

    /** Falhas consecutivas desde o último pagamento com sucesso — base da janela/tentativas de {@code PAST_DUE}. */
    public FailureStreak failureStreak(UUID subscriptionId) {
        Timestamp lastSuccess = jdbcClient.sql("""
                        SELECT max(created_at) FROM payment WHERE subscription_id = :subscriptionId AND status = 'SUCCEEDED'
                        """)
                .param("subscriptionId", subscriptionId)
                .query(Timestamp.class)
                .optional()
                .orElse(null);
        String sql = lastSuccess == null
                ? "SELECT count(*), min(created_at) FROM payment WHERE subscription_id = :subscriptionId AND status = 'FAILED'"
                : "SELECT count(*), min(created_at) FROM payment WHERE subscription_id = :subscriptionId AND status = 'FAILED' AND created_at > :lastSuccess";
        var spec = jdbcClient.sql(sql).param("subscriptionId", subscriptionId);
        if (lastSuccess != null) {
            spec = spec.param("lastSuccess", lastSuccess);
        }
        return spec.query((ResultSet rs, int rowNum) -> {
                    long count = rs.getLong(1);
                    Timestamp since = rs.getTimestamp(2);
                    return new FailureStreak(count, since == null ? null : since.toInstant());
                })
                .single();
    }

    public record FailureStreak(long attempts, Instant since) {
    }
}
