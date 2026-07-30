package pt.servimatch.modules.users.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Acesso à tabela {@code users} (V2). {@code keycloak_sub} é a chave
 * estável de correlação com o IdP (ADR-0002) — nunca o email.
 *
 * <p>{@code @Lazy}: adia a resolução de {@link JdbcClient} para o primeiro
 * uso real. Sem isto, qualquer teste que arranque o contexto Spring
 * completo sem {@code DataSource} configurado (ex.: testes de
 * {@code backend-platform} anteriores a esta onda, que não previam módulos
 * com persistência) falha no arranque só por este bean existir — mesmo que
 * nunca seja chamado. Não muda o comportamento em produção (permanece
 * singleton, criado uma vez, na primeira utilização em vez de no arranque).
 */
@Repository
@Lazy
class UserRepository {

    private final JdbcClient jdbcClient;

    UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Optional<UserRow> findByKeycloakSub(String keycloakSub) {
        return jdbcClient.sql("""
                        SELECT id, keycloak_sub, email, display_name, status
                        FROM users WHERE keycloak_sub = :sub
                        """)
                .param("sub", keycloakSub)
                .query(this::mapRow)
                .optional();
    }

    Optional<UserRow> findById(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, keycloak_sub, email, display_name, status
                        FROM users WHERE id = :id
                        """)
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    /**
     * Tradução em lote {@code id → (display_name)} — uma única consulta
     * {@code IN (:ids)}, nunca um {@code SELECT} por id (ver
     * {@code UsersApi#findByIds}). Chamador garante {@code ids} não vazio.
     */
    List<UserRow> findByIds(Set<UUID> ids) {
        return jdbcClient.sql("""
                        SELECT id, keycloak_sub, email, display_name, status
                        FROM users WHERE id IN (:ids)
                        """)
                .param("ids", ids)
                .query(this::mapRow)
                .list();
    }

    /**
     * Provisionamento JIT idempotente: em caso de corrida entre dois
     * pedidos concorrentes do mesmo utilizador novo, o {@code ON CONFLICT}
     * garante que só uma linha é criada; a chamada perdedora não recebe
     * linha de volta e o chamador (service) tem de voltar a ler por
     * {@code keycloak_sub}.
     */
    Optional<UUID> insertIfAbsent(String keycloakSub, String email, String displayName) {
        return jdbcClient.sql("""
                        INSERT INTO users (keycloak_sub, email, display_name)
                        VALUES (:sub, :email, :displayName)
                        ON CONFLICT (keycloak_sub) DO NOTHING
                        RETURNING id
                        """)
                .param("sub", keycloakSub)
                .param("email", email)
                .param("displayName", displayName)
                .query(UUID.class)
                .optional();
    }

    private UserRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UserRow(
                (UUID) rs.getObject("id"),
                rs.getString("keycloak_sub"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("status"));
    }
}
