package pt.servimatch.testsupport;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Contentor Postgres/PostGIS partilhado para os testes de integração dos
 * módulos {@code billing}/{@code payments} — prova as garantias de
 * idempotência/ordenação contra uma base de dados real (CLAUDE.md,
 * critérios de aceitação: "provado contra a BD real, não com mock").
 *
 * <p>Usa {@code org.testcontainers:postgresql} (agora disponível em
 * {@code backend/pom.xml}, agente {@code backend-platform} —
 * {@code pt.servimatch.platform.TestcontainersPostgisMigrationTest} é a
 * prova de referência de que a dependência funciona). Padrão "singleton
 * container": uma única instância partilhada por toda a JVM de testes
 * (evita arrancar um contentor por classe de teste); o Ryuk do
 * Testcontainers trata da paragem no final da JVM — sem gestão manual de
 * ciclo de vida por parte deste ficheiro.
 *
 * <p>Todo o schema (V1–V14) é aplicado uma vez via Flyway, à semelhança de
 * {@code TestcontainersPostgisMigrationTest}. Os testes que consomem este
 * suporte isolam-se por dados com identificadores aleatórios (UUID), não
 * por limpeza entre testes — o contentor nunca é reiniciado a meio da
 * suite.
 */
public final class SharedPostgis {

    private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("servimatch_test")
            .withUsername("servimatch_test")
            .withPassword("servimatch_test");

    private static volatile boolean migrated = false;

    private SharedPostgis() {
    }

    public static synchronized String jdbcUrl() {
        ensureStarted();
        return CONTAINER.getJdbcUrl();
    }

    public static String username() {
        return CONTAINER.getUsername();
    }

    public static String password() {
        return CONTAINER.getPassword();
    }

    private static void ensureStarted() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        if (!migrated) {
            Flyway.configure()
                    .dataSource(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .load()
                    .migrate();
            migrated = true;
        }
    }
}
