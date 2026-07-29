package pt.servimatch.modules.chat.internal;

import java.time.Instant;
import java.util.UUID;

record MessageRow(UUID id, UUID conversationId, UUID senderId, String body, Instant sentAt, Instant readAt) {
}
