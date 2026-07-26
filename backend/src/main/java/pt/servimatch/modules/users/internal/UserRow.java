package pt.servimatch.modules.users.internal;

import java.util.UUID;

/** Projeção mínima da linha {@code users} usada dentro do módulo. */
record UserRow(UUID id, String keycloakSub, String email, String displayName, String status) {
}
