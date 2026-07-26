package pt.servimatch.modules.requests.internal.web;

import java.util.UUID;

public record CategoryDto(UUID id, UUID parentId, String slug, String name, boolean active) {
}
