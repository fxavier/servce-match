package pt.servimatch.modules.users.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.users.UsersApi;
import pt.servimatch.platform.security.UserProvisioningPort;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository.
/**
 * Implementa {@link UsersApi} (API do módulo, usada dentro do monólito) e
 * {@link UserProvisioningPort} (porta definida por {@code platform.security},
 * ADR-0012 D9, consumida por {@code UserProvisioningFilter} para todo o
 * pedido autenticado). Um único bean para as duas: ambas resolvem para a
 * mesma operação idempotente sobre {@code users}, e este módulo é — por
 * decisão do ADR-0012 D4 — o único escritor dessa tabela em produção.
 */
@Service
@Lazy
class UsersService implements UsersApi, UserProvisioningPort {

    private final UserRepository repository;

    UsersService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public UUID ensureProvisioned(Jwt jwt) {
        String sub = jwt.getSubject();
        String email = firstNonBlank(jwt.getClaimAsString("email"), jwt.getClaimAsString("preferred_username"), sub);
        String displayName = firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username"), email);
        return ensureProvisionedId(sub, email, displayName);
    }

    /**
     * {@link UserProvisioningPort}: mesma operação idempotente de
     * {@link #ensureProvisioned(Jwt)}, chamada uma vez por {@code sub} novo
     * pelo {@code UserProvisioningFilter} (que já filtra por cache local —
     * ver o seu javadoc) antes de qualquer endpoint de módulo correr.
     */
    @Override
    @Transactional
    public void ensureProvisioned(String sub, String email, String displayName) {
        ensureProvisionedId(sub, email, displayName);
    }

    private UUID ensureProvisionedId(String sub, String email, String displayName) {
        Optional<UserRow> existing = repository.findByKeycloakSub(sub);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        return repository.insertIfAbsent(sub, email, displayName)
                // Corrida perdida: outra transação já inseriu entretanto; a linha existe agora.
                .orElseGet(() -> repository.findByKeycloakSub(sub)
                        .orElseThrow(() -> new IllegalStateException("Provisionamento JIT falhou para sub=" + sub))
                        .id());
    }

    @Override
    public Optional<UserView> findById(UUID userId) {
        return repository.findById(userId).map(row -> new UserView(row.id(), row.displayName(), row.email()));
    }

    @Override
    public Map<UUID, UserSummaryView> findByIds(Set<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByIds(userIds).stream()
                .collect(Collectors.toMap(UserRow::id, row -> new UserSummaryView(row.id(), row.displayName())));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException("Nenhum valor não-vazio disponível.");
    }
}
