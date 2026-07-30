package pt.servimatch.modules.chat.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code conversation}/{@code message}/{@code message_attachment}
 * (V9).
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

    /**
     * Página de {@code listConversations} para o participante autenticado —
     * autorização feita <b>no WHERE</b>: só linhas onde {@code viewerId} é o
     * {@code customer_id}, ou {@code viewerProviderId} (pode ser
     * {@code null} se o autenticado não tiver perfil de prestador — a
     * comparação com {@code NULL} nunca corresponde, o que exclui a
     * segunda condição em vez de a filtrar depois de carregar) é o
     * {@code provider_id}. Nenhuma linha de outro participante chega à
     * aplicação.
     *
     * <p>Ordena por {@code COALESCE(last_message_at, created_at) DESC, id
     * DESC} (V20) — desempate por {@code id} obrigatório: sem ele, duas
     * conversas com exatamente o mesmo instante de atividade fariam o
     * cursor saltar ou repetir uma linha (mesmo defeito documentado em
     * {@link #findPage}). {@code unread_count} é uma subconsulta indexada
     * por {@code idx_message_conversation_id_sent_at} (V9) sobre a marca de
     * água por participante ({@code last_read_by_customer_at}/
     * {@code last_read_by_provider_at}, V20) — nunca uma chamada por
     * conversa a partir da aplicação.
     */
    List<ConversationSummaryRow> findPageForParticipant(UUID viewerId, UUID viewerProviderId, CursorCodec.Position after, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.request_id, c.customer_id, c.provider_id,
                       c.last_message_at, c.last_message_preview,
                       (SELECT count(*) FROM message m2
                         WHERE m2.conversation_id = c.id
                           AND m2.sender_id <> :viewerId
                           AND m2.sent_at > COALESCE(
                               CASE WHEN c.customer_id = :viewerId
                                    THEN c.last_read_by_customer_at
                                    ELSE c.last_read_by_provider_at END,
                               '-infinity'::timestamptz)
                       ) AS unread_count,
                       COALESCE(c.last_message_at, c.created_at) AS sort_at
                FROM conversation c
                WHERE (c.customer_id = :viewerId OR c.provider_id = :viewerProviderId)
                """);
        if (after != null) {
            sql.append(" AND (COALESCE(c.last_message_at, c.created_at), c.id) < (:afterSortAt, :afterId) ");
        }
        sql.append(" ORDER BY sort_at DESC, c.id DESC LIMIT :limit ");

        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString())
                .param("viewerId", viewerId)
                .param("viewerProviderId", viewerProviderId, Types.OTHER)
                .param("limit", limit);
        if (after != null) {
            spec = spec.param("afterSortAt", Timestamp.from(after.sentAt()))
                    .param("afterId", after.id());
        }
        return spec.query((rs, rowNum) -> mapConversationSummary(rs)).list();
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

    private static ConversationSummaryRow mapConversationSummary(ResultSet rs) throws SQLException {
        Timestamp lastMessageAt = rs.getTimestamp("last_message_at");
        return new ConversationSummaryRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("request_id"),
                (UUID) rs.getObject("customer_id"),
                (UUID) rs.getObject("provider_id"),
                lastMessageAt == null ? null : lastMessageAt.toInstant(),
                rs.getString("last_message_preview"),
                rs.getInt("unread_count"),
                rs.getTimestamp("sort_at").toInstant());
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
