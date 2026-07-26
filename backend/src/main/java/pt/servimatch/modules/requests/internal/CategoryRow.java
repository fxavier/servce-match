package pt.servimatch.modules.requests.internal;

import java.util.UUID;

record CategoryRow(UUID id, UUID parentId, String slug, String name, boolean active) {
}
