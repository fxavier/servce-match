package pt.servimatch.testsupport;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.UUID;

/** Fábrica de {@link DataSource}/{@link JdbcClient} ligados a {@link SharedPostgis}, e fixtures mínimas reutilizáveis. */
public final class TestDatabase {

    private TestDatabase() {
    }

    public static DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(SharedPostgis.jdbcUrl());
        dataSource.setUser(SharedPostgis.username());
        dataSource.setPassword(SharedPostgis.password());
        return dataSource;
    }

    public static JdbcClient jdbcClient() {
        return JdbcClient.create(dataSource());
    }

    /** Cria um {@code users} + {@code provider_profile} descartáveis e devolve o {@code provider_profile.id}. */
    public static UUID createProvider(JdbcClient jdbcClient) {
        UUID userId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        String suffix = userId.toString();
        jdbcClient.sql("""
                        INSERT INTO users (id, keycloak_sub, email, display_name)
                        VALUES (:id, :sub, :email, 'Test Provider')
                        """)
                .param("id", userId)
                .param("sub", "kc-" + suffix)
                .param("email", "provider-" + suffix + "@example.test")
                .update();
        jdbcClient.sql("""
                        INSERT INTO provider_profile (id, user_id)
                        VALUES (:id, :userId)
                        """)
                .param("id", providerId)
                .param("userId", userId)
                .update();
        return providerId;
    }

    /** Cria um {@code subscription_plan} descartável e devolve o seu {@code id}. */
    public static UUID createPlan(JdbcClient jdbcClient, long priceAmountCents) {
        UUID planId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO subscription_plan (id, code, name, price_amount_cents)
                        VALUES (:id, :code, 'Test Plan', :price)
                        """)
                .param("id", planId)
                .param("code", "test-" + planId)
                .param("price", priceAmountCents)
                .update();
        return planId;
    }
}
