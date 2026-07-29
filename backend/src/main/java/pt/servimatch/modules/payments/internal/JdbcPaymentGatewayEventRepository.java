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
 * dados (V12), não uma verificação em memória. {@link #insertVerified}
 * usa {@code ON CONFLICT DO NOTHING}: se não inserir nada, é uma
 * reentrega e o chamador não repete efeito de domínio nenhum.
 *
 * <p><b>Essa chave é reservada a eventos com assinatura verificada.</b>
 * Um evento com assinatura inválida nunca é gravado como
 * {@code (gateway, raw_event_id)} real — ver {@link #insertQuarantined}
 * — porque isso permitiria a um atacante que preveja/conheça o
 * {@code raw_event_id} de um evento futuro genuíno (plausível em
 * Eupago/IfthenPay, cujo id deriva de {@code transactionId} ou de
 * {@code identifier:referência}, dados que o próprio prestador conhece
 * sobre a sua própria subscrição) ocupar essa linha primeiro e bloquear o
 * evento real para sempre: o {@code INSERT} genuíno bateria no
 * {@code ON CONFLICT DO NOTHING} e seria tratado como duplicado.
 */
@Repository
public class JdbcPaymentGatewayEventRepository {

    private final JdbcClient jdbcClient;

    public JdbcPaymentGatewayEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Regista um evento com assinatura <b>já verificada</b>
     * ({@code PaymentGateway#verifySignature} devolveu {@code true}), de
     * forma idempotente pela chave real {@code (gateway, raw_event_id)}.
     * Nunca chamar isto antes de verificar a assinatura — ver a nota da
     * classe.
     *
     * @return o {@code id} da linha inserida, ou vazio se {@code (gateway, rawEventId)} já existia (duplicado).
     */
    public Optional<UUID> insertVerified(String gateway, String rawEventId, String eventType, String payloadJson) {
        UUID id = UUID.randomUUID();
        int inserted = jdbcClient.sql("""
                        INSERT INTO payment_gateway_event
                            (id, gateway, raw_event_id, event_type, payload, signature_verified, processing_status, error_detail)
                        VALUES
                            (:id, :gateway, :rawEventId, :eventType, :payload::jsonb, true, 'RECEIVED', null)
                        ON CONFLICT (gateway, raw_event_id) DO NOTHING
                        """)
                .param("id", id)
                .param("gateway", gateway)
                .param("rawEventId", rawEventId)
                .param("eventType", eventType)
                .param("payload", payloadJson)
                .update();
        return inserted > 0 ? Optional.of(id) : Optional.empty();
    }

    /**
     * Regista, para investigação, um evento cuja assinatura falhou a
     * verificação — mas sob uma chave <b>sintética e sempre única</b>
     * ({@code "quarantine:" + id da linha}), nunca sob o
     * {@code raw_event_id} alegado pelo remetente. Isto é o que impede o
     * envenenamento descrito na nota da classe: o espaço de chaves
     * {@code (gateway, raw_event_id)} usado por {@link #insertVerified}
     * fica sempre livre para o evento genuíno, mesmo que um forjado com o
     * mesmo id alegado tenha chegado primeiro.
     *
     * <p>O corpo completo continua gravado ({@code payload}) e o
     * {@code raw_event_id} que o remetente alegou fica em
     * {@code error_detail}, para que a auditoria/forense não perca nada
     * do que a gravação anterior dava — só deixa de competir pela
     * unicidade real.
     *
     * @return o {@code id} da linha de quarentena inserida (sempre inserida; não há conflito possível).
     */
    public UUID insertQuarantined(String gateway, String allegedRawEventId, String eventType, String payloadJson) {
        UUID id = UUID.randomUUID();
        String quarantineKey = "quarantine:" + id;
        jdbcClient.sql("""
                        INSERT INTO payment_gateway_event
                            (id, gateway, raw_event_id, event_type, payload, signature_verified, processing_status, error_detail)
                        VALUES
                            (:id, :gateway, :rawEventId, :eventType, :payload::jsonb, false, 'FAILED', :errorDetail)
                        """)
                .param("id", id)
                .param("gateway", gateway)
                .param("rawEventId", quarantineKey)
                .param("eventType", eventType)
                .param("payload", payloadJson)
                .param("errorDetail", "invalid signature; alleged_raw_event_id=" + allegedRawEventId)
                .update();
        return id;
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
