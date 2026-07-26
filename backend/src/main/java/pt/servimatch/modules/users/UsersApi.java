package pt.servimatch.modules.users;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

/**
 * API pública do módulo {@code users}. O {@code sub} do token Keycloak é a
 * chave estável de identidade (ADR-0002, ARQUITETURA §8.5); o registo de
 * domínio é provisionado <em>just-in-time</em> no primeiro pedido
 * autenticado, nunca chaveado por email.
 */
public interface UsersApi {

    /**
     * Garante que existe um registo {@code User} de domínio para o
     * principal do token, criando-o na primeira chamada. Idempotente: em
     * caso de corrida entre dois pedidos concorrentes do mesmo utilizador
     * novo, ambos resolvem para o mesmo {@code id}.
     */
    UUID ensureProvisioned(Jwt jwt);

    Optional<UserView> findById(UUID userId);

    record UserView(UUID id, String displayName, String email) {
    }
}
