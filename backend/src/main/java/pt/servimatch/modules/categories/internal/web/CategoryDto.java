package pt.servimatch.modules.categories.internal.web;

import java.util.UUID;

/** Espelha {@code docs/api/openapi.yaml#/components/schemas/Category}. */
public record CategoryDto(UUID id, UUID parentId, String slug, String name, boolean active) {
}
