package pt.servimatch.modules.chat.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha de {@code listConversations} (V20 {@code conversation.last_message_at}/
 * {@code last_message_preview}, denormalizados por trigger em cada
 * {@code INSERT} de {@code message} — não há LATERAL nem agregação em
 * memória para obter a última mensagem). {@code sortAt} é
 * {@code COALESCE(last_message_at, created_at)}, calculado em SQL (V20,
 * mesma expressão indexada por {@code idx_conversation_customer_id_activity}/
 * {@code idx_conversation_provider_id_activity}) — uma conversa sem
 * mensagens ordena pela sua própria criação, nunca por {@code NULL}.
 */
record ConversationSummaryRow(
        UUID id,
        UUID requestId,
        UUID customerId,
        UUID providerId,
        Instant lastMessageAt,
        String lastMessagePreview,
        int unreadCount,
        Instant sortAt) {
}
