package pt.servimatch.modules.billing.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pt.servimatch.modules.billing.ProviderAccountResolver;

import java.util.Optional;
import java.util.UUID;

/**
 * Ver javadoc de {@link ProviderAccountResolver}: leitura só-de-consulta
 * pragmática a {@code users}/{@code provider_profile} (tabelas de outros
 * módulos), documentada e assinalada para substituição futura.
 */
@Component
public class JdbcProviderAccountResolver implements ProviderAccountResolver {

    private final JdbcClient jdbcClient;

    public JdbcProviderAccountResolver(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<UUID> resolveProviderId(String keycloakSubject) {
        if (keycloakSubject == null || keycloakSubject.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
                        SELECT pp.id
                        FROM provider_profile pp
                        JOIN users u ON u.id = pp.user_id
                        WHERE u.keycloak_sub = :sub
                        """)
                .param("sub", keycloakSubject)
                .query(UUID.class)
                .optional();
    }
}
