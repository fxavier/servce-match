package pt.servimatch.modules.users.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import pt.servimatch.modules.users.UsersApi;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void userProvisioningPortEnsureProvisionedIsIdempotentLikeTheJwtVariant() {
        service = new UsersService(repository);
        String sub = "keycloak-sub-port";
        when(repository.findByKeycloakSub(sub)).thenReturn(Optional.empty());
        when(repository.insertIfAbsent(sub, "port@b.pt", "Porta")).thenReturn(Optional.of(UUID.randomUUID()));

        service.ensureProvisioned(sub, "port@b.pt", "Porta");

        verify(repository).insertIfAbsent(sub, "port@b.pt", "Porta");
    }

    @Test
    void userProvisioningPortDoesNotInsertWhenTheRowAlreadyExists() {
        service = new UsersService(repository);
        String sub = "keycloak-sub-port-existing";
        when(repository.findByKeycloakSub(sub))
                .thenReturn(Optional.of(new UserRow(UUID.randomUUID(), sub, "e@b.pt", "Existing", "ACTIVE")));

        service.ensureProvisioned(sub, "e@b.pt", "Existing");

        verify(repository, never()).insertIfAbsent(eq(sub), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void findByIdsReturnsAMapWithoutEmailKeyedById() {
        service = new UsersService(repository);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(repository.findByIds(Set.of(id1, id2))).thenReturn(List.of(
                new UserRow(id1, "kc-1", "one@b.pt", "Um", "ACTIVE"),
                new UserRow(id2, "kc-2", "two@b.pt", "Dois", "ACTIVE")));

        Map<UUID, UsersApi.UserSummaryView> result = service.findByIds(Set.of(id1, id2));

        assertThat(result).containsEntry(id1, new UsersApi.UserSummaryView(id1, "Um"));
        assertThat(result).containsEntry(id2, new UsersApi.UserSummaryView(id2, "Dois"));
    }

    @Test
    void findByIdsWithNullOrEmptyIdsNeverTouchesTheDatabase() {
        service = new UsersService(repository);

        assertThat(service.findByIds(null)).isEmpty();
        assertThat(service.findByIds(Set.of())).isEmpty();

        verifyNoInteractions(repository);
    }

    @Test
    void findByIdsOmitsUnknownIdsInsteadOfThrowing() {
        service = new UsersService(repository);
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(repository.findByIds(Set.of(known, unknown)))
                .thenReturn(List.of(new UserRow(known, "kc", "k@b.pt", "Conhecido", "ACTIVE")));

        Map<UUID, UsersApi.UserSummaryView> result = service.findByIds(Set.of(known, unknown));

        assertThat(result).containsOnlyKeys(known);
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
