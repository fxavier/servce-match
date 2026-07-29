package pt.servimatch.modules.chat.internal;

import java.util.UUID;

record MessageAttachmentRow(UUID messageId, UUID imageAssetId, int position) {
}
