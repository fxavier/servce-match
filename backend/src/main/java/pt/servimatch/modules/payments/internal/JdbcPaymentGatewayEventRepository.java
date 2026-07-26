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
 * Persistência de {@code payment_gateway_event} — a garantia de
 * idempotência é o {@code UNIQUE (gateway, raw_event_id)} da base de
 * dados (V12), não uma verificação em memória. {@link #insertIfAbsent}
 * usa {@code ON CONFLICT DO NOTHING}: se não inserir nada, é uma
 * reentrega e o chamador não repete efeito de domínio nenhum.
 */
@Repository
public class JdbcPaymentGatewayEventRepository {

    private final JdbcClient jdbcClient;

    public JdbcPaymentGatewayEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** @return o {@code id} da linha inserida, ou vazio se {@code (gateway, rawEventId)} já existia (duplicado). */
    public Optional<UUID> insertIfAbsent(String gateway, String rawEventId, String eventType, String payloadJson, boolean signatureVerified) {
        UUID id = UUID.randomUUID();
        String processingStatus = signatureVerified ? "RECEIVED" : "FAILED";
        String errorDetail = signatureVerified ? null : "invalid signature";
        int inserted = jdbcClient.sql("""
                        INSERT INTO payment_gateway_event
                            (id, gateway, raw_event_id, event_type, payload, signature_verified, processing_status, error_detail)
                        VALUES
                            (:id, :gateway, :rawEventId, :eventType, :payload::jsonb, :signatureVerified, :processingStatus, :errorDetail)
                        ON CONFLICT (gateway, raw_event_id) DO NOTHING
                        """)
                .param("id", id)
                .param("gateway", gateway)
                .param("rawEventId", rawEventId)
                .param("eventType", eventType)
                .param("payload", payloadJson)
                .param("signatureVerified", signatureVerified)
                .param("processingStatus", processingStatus)
                .param("errorDetail", errorDetail)
                .update();
        return inserted > 0 ? Optional.of(id) : Optional.empty();
    }

    public void markProcessed(UUID id) {
        jdbcClient.sql("UPDATE payment_gateway_event SET processing_status = 'PROCESSED', processed_at = now() WHERE id = :id")
                .param("id", id)
                .update();
    }

    public void markIgnored(UUID id, String reason) {
        jdbcClient.sql("UPDATE payment_gateway_event SET processing_status = 'IGNORED', error_detail = :reason, processed_at = now() WHERE id = :id")
                .param("id", id)
                .param("reason", reason)
                .update();
    }

    public void markFailed(UUID id, String errorDetail) {
        jdbcClient.sql("UPDATE payment_gateway_event SET processing_status = 'FAILED', error_detail = :errorDetail, processed_at = now() WHERE id = :id")
                .param("id", id)
                .param("errorDetail", errorDetail)
                .update();
    }

    /**
     * Eventos verificados mas ainda não concluídos (falha transitória de
     * processamento ou reinício a meio) — candidatos a retentativa pelo
     * job de reconciliação. {@code olderThan} evita correr atrás de um
     * evento que ainda está a ser processado na mesma transação síncrona.
     */
    public java.util.List<StoredEvent> findStaleUnprocessed(Instant olderThan) {
        return jdbcClient.sql("""
                        SELECT id, gateway, raw_event_id, event_type, payload::text AS payload_text,
                               signature_verified, processing_status, received_at
                        FROM payment_gateway_event
                        WHERE signature_verified = true
                          AND processing_status IN ('RECEIVED', 'FAILED')
                          AND received_at < :olderThan
                        """)
                .param("olderThan", Timestamp.from(olderThan))
                .query(JdbcPaymentGatewayEventRepository::mapRow)
                .list();
    }

    public Optional<StoredEvent> findById(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, gateway, raw_event_id, event_type, payload::text AS payload_text,
                               signature_verified, processing_status, received_at
                        FROM payment_gateway_event WHERE id = :id
                        """)
                .param("id", id)
                .query(JdbcPaymentGatewayEventRepository::mapRow)
                .optional();
    }

    /** Contagem de linhas por {@code (gateway, raw_event_id)} — usada em testes para provar idempotência ao nível da BD. */
    public long countByGatewayAndRawEventId(String gateway, String rawEventId) {
        return jdbcClient.sql("SELECT count(*) FROM payment_gateway_event WHERE gateway = :gateway AND raw_event_id = :rawEventId")
                .param("gateway", gateway)
                .param("rawEventId", rawEventId)
                .query(Long.class)
                .single();
    }

    private static StoredEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StoredEvent(
                (UUID) rs.getObject("id"),
                rs.getString("gateway"),
                rs.getString("raw_event_id"),
                rs.getString("event_type"),
                rs.getString("payload_text"),
                rs.getBoolean("signature_verified"),
                rs.getString("processing_status"),
                toInstant(rs.getTimestamp("received_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record StoredEvent(UUID id, String gateway, String rawEventId, String eventType, String payloadJson,
                               boolean signatureVerified, String processingStatus, Instant receivedAt) {
    }
}
