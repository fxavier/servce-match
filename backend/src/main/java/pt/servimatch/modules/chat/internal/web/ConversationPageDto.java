package pt.servimatch.modules.chat.internal.web;

import java.util.List;

public record ConversationPageDto(List<ConversationSummaryDto> items, PageMetaDto page) {
}
