package pt.servimatch.platform;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova mínima de que {@code org.testcontainers:postgresql} e
 * {@code org.testcontainers:junit-jupiter} (backend/pom.xml) funcionam de
 * facto, não apenas declarados: arranca um {@code postgis/postgis:16-3.4}
 * real, aplica todas as migrações Flyway de {@code db/migration} e confirma
 * o estado resultante do schema. É a base sobre a qual os módulos de
 * domínio (`backend-domain`, `backend-matching`, `backend-payments`) devem
 * construir as suas próprias suítes de integração — ver CLAUDE.md §5 e o
 * pedido do agente `backend-matching` que motivou esta dependência.
 */
@Testcontainers
class TestcontainersPostgisMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("servimatch")
            .withUsername("servimatch")
            .withPassword("servimatch");

    @Test
    void appliesAllMigrationsAgainstARealPostgisContainer() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThan(0);

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet extensions = statement.executeQuery(
                     "select extname from pg_extension where extname in ('postgis', 'pg_trgm', 'unaccent')")) {
            int found = 0;
            while (extensions.next()) {
                found++;
            }
            assertThat(found).isEqualTo(3);
        }
    }
}
