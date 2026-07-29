package pt.servimatch.modules.chat.internal.web;

import java.util.UUID;

public record ImageRefDto(UUID id, String url, String contentType) {
}
