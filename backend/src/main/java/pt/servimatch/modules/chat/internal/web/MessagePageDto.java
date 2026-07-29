package pt.servimatch.modules.chat.internal.web;

import java.util.List;

public record MessagePageDto(List<MessageDto> items, PageMetaDto page) {
}
