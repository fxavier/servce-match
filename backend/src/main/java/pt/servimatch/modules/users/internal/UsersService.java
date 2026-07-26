package pt.servimatch.modules.users.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.users.UsersApi;

import java.util.Optional;
import java.util.UUID;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository.
@Service
@Lazy
class UsersService implements UsersApi {

    private final UserRepository repository;

    UsersService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public UUID ensureProvisioned(Jwt jwt) {
        String sub = jwt.getSubject();
        Optional<UserRow> existing = repository.findByKeycloakSub(sub);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        String email = firstNonBlank(jwt.getClaimAsString("email"), jwt.getClaimAsString("preferred_username"), sub);
        String displayName = firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username"), email);
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException("Nenhum valor não-vazio disponível.");
    }
}
