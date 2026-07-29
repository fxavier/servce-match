package pt.servimatch.modules.chat.internal;

import java.time.Instant;
import java.util.UUID;

record ConversationRow(UUID id, UUID requestId, UUID customerId, UUID providerId, Instant createdAt) {
}
