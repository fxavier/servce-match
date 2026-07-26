package pt.servimatch.modules.requests.internal.web;

import java.util.UUID;

public record ImageRefDto(UUID id, String url, String contentType) {
}
