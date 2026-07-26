package pt.servimatch.modules.users.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Provisionamento <em>just-in-time</em> (ADR-0002, ARQUITETURA §8.5):
 * {@code keycloak_sub} é a chave estável, nunca o email; a corrida entre
 * dois pedidos concorrentes do primeiro login resolve para o mesmo utilizador.
 */
@ExtendWith(MockitoExtension.class)
class UsersServiceTest {

    @Mock
    private UserRepository repository;

    private UsersService service;

    @Test
    void ensureProvisionedReturnsExistingUserWithoutInserting() {
        service = new UsersService(repository);
        String sub = "keycloak-sub-123";
        UUID existingId = UUID.randomUUID();
        when(repository.findByKeycloakSub(sub))
                .thenReturn(Optional.of(new UserRow(existingId, sub, "a@b.pt", "Ana", "ACTIVE")));

        UUID result = service.ensureProvisioned(jwt(sub, "a@b.pt", "Ana"));

        assertThat(result).isEqualTo(existingId);
        verify(repository, never()).insertIfAbsent(eq(sub), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ensureProvisionedCreatesUserOnFirstLogin() {
        service = new UsersService(repository);
        String sub = "keycloak-sub-new";
        UUID newId = UUID.randomUUID();
        when(repository.findByKeycloakSub(sub)).thenReturn(Optional.empty());
        when(repository.insertIfAbsent(sub, "new@b.pt", "Nova")).thenReturn(Optional.of(newId));

        UUID result = service.ensureProvisioned(jwt(sub, "new@b.pt", "Nova"));

        assertThat(result).isEqualTo(newId);
    }

    @Test
    void ensureProvisionedFallsBackToExistingRowWhenConcurrentInsertWonTheRace() {
        service = new UsersService(repository);
        String sub = "keycloak-sub-race";
        UUID winnerId = UUID.randomUUID();
        when(repository.findByKeycloakSub(sub))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new UserRow(winnerId, sub, "race@b.pt", "Race", "ACTIVE")));
        when(repository.insertIfAbsent(sub, "race@b.pt", "Race")).thenReturn(Optional.empty());

        UUID result = service.ensureProvisioned(jwt(sub, "race@b.pt", "Race"));

        assertThat(result).isEqualTo(winnerId);
    }

    private static Jwt jwt(String sub, String email, String name) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", sub, "email", email, "name", name));
    }
}
