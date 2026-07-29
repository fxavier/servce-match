package pt.servimatch.modules.chat.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code conversation}/{@code message}/{@code message_attachment}
 * (V9).
 *
 * <p><b>Nota de fronteira (SQL nativo, ADR-0010 §Decisão — leitura
 * <em>set-based</em> permitida sob condições, escrita proibida):</b>
 * {@link #findProviderIdByUserId} e {@link #isProviderVisible} leem
 * {@code provider_profile} diretamente por SQL, sem importar nenhuma classe
 * de {@code pt.servimatch.modules.providers} — {@code chat.package-info}
 * (propriedade do {@code backend-platform}) não inclui {@code providers} em
 * {@code allowedDependencies}. Mesmo padrão que
 * {@code matching.internal.EligibilityRepository}: é o único jeito de
 * autorizar "o prestador X é participante desta conversa" e de aplicar o
 * <em>gating</em> por subscrição de {@code ARQUITETURA.md §3.3}
 * ({@code visibility_state = 'VISIBLE'}, o mesmo booleano que
 * {@code ProvidersApi.checkEligibility(...).visible()} expõe).
 *
 * <p><b>Importante:</b> esta leitura <em>não está</em> na tabela enumerada
 * do ADR-0010 §Contexto (que só cobre {@code matching}, {@code search},
 * {@code billing}, {@code requests}) — o próprio ADR exige que uma leitura
 * nova entre módulos seja pedida ao {@code arquiteto}, não decidida por
 * quem a precisa. Fica sinalizado explicitamente no relatório de entrega
 * como pedido de adição à tabela do ADR-0010 (ou, em alternativa, de
 * {@code providers} a {@code allowedDependencies} de {@code chat}) — não
 * decidido unilateralmente aqui.
 *
 * <p>{@code @Lazy}: ver nota equivalente em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@Repository
@Lazy
class ConversationRepository {

    private final JdbcClient jdbcClient;

    ConversationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Idempotente via {@code uq_conversation_request_provider} (V9) — a
     * entrega <em>at-least-once</em> de {@code ProposalAccepted}
     * ({@code @ApplicationModuleListener}) pode invocar isto mais que uma
     * vez para o mesmo par (pedido, prestador).
     */
    UUID createIfAbsent(UUID requestId, UUID customerId, UUID providerId) {
        Optional<UUID> inserted = jdbcClient.sql("""
                        INSERT INTO conversation (request_id, customer_id, provider_id)
                        VALUES (:requestId, :customerId, :providerId)
                        ON CONFLICT (request_id, provider_id) DO NOTHING
                        RETURNING id
                        """)
                .param("requestId", requestId)
                .param("customerId", customerId)
                .param("providerId", providerId)
                .query(UUID.class)
                .optional();
        return inserted.orElseGet(() -> jdbcClient.sql("""
                        SELECT id FROM conversation WHERE request_id = :requestId AND provider_id = :providerId
                        """)
                .param("requestId", requestId)
                .param("providerId", providerId)
                .query(UUID.class)
                .single());
    }

    Optional<ConversationRow> findById(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, request_id, customer_id, provider_id, created_at
                        FROM conversation WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> mapConversation(rs))
                .optional();
    }

    /** {@code provider_profile.id} do prestador por trás de {@code userId} — ver nota de fronteira na classe. */
    Optional<UUID> findProviderIdByUserId(UUID userId) {
        return jdbcClient.sql("SELECT id FROM provider_profile WHERE user_id = :userId")
                .param("userId", userId)
                .query(UUID.class)
                .optional();
    }

    /** {@code visibility_state = 'VISIBLE'} — derivado de subscrição ativa (ARQUITETURA §3.3/§12.1). */
    boolean isProviderVisible(UUID providerId) {
        return Boolean.TRUE.equals(jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM provider_profile WHERE id = :id AND visibility_state = 'VISIBLE'
                        )
                        """)
                .param("id", providerId)
                .query(Boolean.class)
                .single());
    }

    UUID insertMessage(UUID conversationId, UUID senderId, String body) {
        return jdbcClient.sql("""
                        INSERT INTO message (conversation_id, sender_id, body)
                        VALUES (:conversationId, :senderId, :body)
                        RETURNING id
                        """)
                .param("conversationId", conversationId)
                .param("senderId", senderId)
                .param("body", body)
                .query(UUID.class)
                .single();
    }

    void linkAttachments(UUID messageId, List<UUID> imageIds) {
        int position = 0;
        for (UUID imageId : imageIds) {
            jdbcClient.sql("""
                            INSERT INTO message_attachment (message_id, image_asset_id, position)
                            VALUES (:messageId, :imageId, :position)
                            ON CONFLICT DO NOTHING
                            """)
                    .param("messageId", messageId)
                    .param("imageId", imageId)
                    .param("position", position++)
                    .update();
        }
    }

    Optional<MessageRow> findMessageById(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, conversation_id, sender_id, body, sent_at, read_at
                        FROM message WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> mapMessage(rs))
                .optional();
    }

    /** Página ordenada por {@code sent_at DESC, id DESC} (ordem cronológica inversa, contrato {@code listMessages}). */
    List<MessageRow> findPage(UUID conversationId, CursorCodec.Position after, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, conversation_id, sender_id, body, sent_at, read_at
                FROM message WHERE conversation_id = :conversationId
                """);
        if (after != null) {
            sql.append(" AND (sent_at, id) < (:afterSentAt, :afterId) ");
        }
        sql.append(" ORDER BY sent_at DESC, id DESC LIMIT :limit ");
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString())
                .param("conversationId", conversationId)
                .param("limit", limit);
        if (after != null) {
            spec = spec.param("afterSentAt", Timestamp.from(after.sentAt()))
                    .param("afterId", after.id());
        }
        return spec.query((rs, rowNum) -> mapMessage(rs)).list();
    }

    /** Anexos de um lote de mensagens, ordenados por posição — usado para resolver via {@code UploadsApi} num único lote. */
    List<MessageAttachmentRow> findAttachmentsForMessages(Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT message_id, image_asset_id, position
                        FROM message_attachment
                        WHERE message_id IN (:messageIds)
                        ORDER BY message_id, position
                        """)
                .param("messageIds", messageIds)
                .query((rs, rowNum) -> new MessageAttachmentRow(
                        (UUID) rs.getObject("message_id"),
                        (UUID) rs.getObject("image_asset_id"),
                        rs.getInt("position")))
                .list();
    }

    private static ConversationRow mapConversation(ResultSet rs) throws SQLException {
        return new ConversationRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("request_id"),
                (UUID) rs.getObject("customer_id"),
                (UUID) rs.getObject("provider_id"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static MessageRow mapMessage(ResultSet rs) throws SQLException {
        Timestamp readAt = rs.getTimestamp("read_at");
        return new MessageRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("conversation_id"),
                (UUID) rs.getObject("sender_id"),
                rs.getString("body"),
                rs.getTimestamp("sent_at").toInstant(),
                readAt == null ? null : readAt.toInstant());
    }
}
