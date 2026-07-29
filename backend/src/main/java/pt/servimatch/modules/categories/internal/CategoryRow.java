package pt.servimatch.modules.categories.internal;

import java.util.UUID;

record CategoryRow(UUID id, UUID parentId, String slug, String name, boolean active) {
}
