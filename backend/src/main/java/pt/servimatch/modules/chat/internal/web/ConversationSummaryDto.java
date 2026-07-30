package pt.servimatch.modules.chat.internal.web;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummaryDto(
        UUID id,
        String counterpartName,
        String counterpartAvatarSeed,
        String lastMessagePreview,
        Instant lastMessageAt,
        int unreadCount,
        String requestTitle) {
}
