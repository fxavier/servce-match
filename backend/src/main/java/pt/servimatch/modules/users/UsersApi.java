package pt.servimatch.modules.users;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Tradução em lote {@code users.id → nome apresentável}, para módulos que
     * precisam do nome de vários utilizadores numa página (ex. lista de
     * conversas, avaliações) sem incorrer em N+1 — nunca chamar
     * {@link #findById} dentro de um ciclo por página.
     *
     * <p>Semântica fixa: uma única consulta; {@code userIds} nulo ou vazio
     * devolve {@link Map#of()} sem tocar na base de dados; identificadores
     * que não correspondem a nenhum utilizador ficam simplesmente ausentes
     * do mapa (nunca lança exceção); não aplica nenhum filtro de autorização
     * — cabe ao chamador decidir se o utilizador autenticado pode ver cada
     * nome devolvido. {@link UserSummaryView} nunca inclui o email: os
     * nomes devolvidos por este método tendem a acabar em respostas
     * públicas (perfis, avaliações), e o email é PII (CLAUDE.md §4).
     */
    Map<UUID, UserSummaryView> findByIds(Set<UUID> userIds);

    record UserView(UUID id, String displayName, String email) {
    }

    /** Projeção sem PII de {@link UserView}, para uso em respostas públicas/em lote — ver {@link #findByIds}. */
    record UserSummaryView(UUID id, String displayName) {
    }
}
