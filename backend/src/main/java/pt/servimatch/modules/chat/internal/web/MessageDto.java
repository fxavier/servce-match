package pt.servimatch.modules.chat.internal.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageDto(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String body,
        List<ImageRefDto> attachments,
        Instant sentAt,
        Instant readAt) {
}
