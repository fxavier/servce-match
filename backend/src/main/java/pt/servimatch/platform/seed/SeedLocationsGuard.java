package pt.servimatch.platform.seed;

import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Terceira camada da barreira do ADR-0013 D3: aborta o arranque se as
 * <em>locations</em> efetivas do Flyway incluírem {@code db/seed} sem que o
 * perfil {@code local} ou {@code dev} esteja ativo — e, adicionalmente,
 * mesmo que um desses esteja ativo, se o perfil {@code prod} também
 * estiver (uma combinação como {@code SPRING_PROFILES_ACTIVE=prod,dev} não
 * é "desenvolvimento com um extra à mistura": é o sinal exato de uma
 * variável herdada por engano de outro ambiente, e é precisamente esse o
 * modo de falha que esta camada existe para transformar num arranque
 * falhado em vez de dados de demonstração em produção).
 *
 * <p><b>Porque {@link FlywayConfigurationCustomizer}, e não um
 * {@code @Component} qualquer com um {@code @PostConstruct}.</b> Este
 * <em>callback</em> do Spring Boot corre depois de
 * {@code spring.flyway.locations} (e qualquer sobreposição por
 * {@code SPRING_FLYWAY_LOCATIONS} no ambiente — 5.º na ordem de
 * {@code PropertySource}, acima do {@code application*.yml}) já ter sido
 * aplicado à {@link FluentConfiguration}, mas <b>antes</b> de
 * {@code Flyway.migrate()} correr. É o único ponto de extensão onde se vê a
 * configuração <em>efetiva</em> — a mesma razão pela qual a camada 2
 * (`application-local.yml`/`application-dev.yml`) sozinha não é barreira:
 * o YAML não é o que corre, é só o que se pretende que corra.
 *
 * <p><b>Porque não chega a camada 1 (exclusão do artefacto).</b> A camada 1
 * garante que {@code db/seed} não existe no <em>classpath</em> de um JAR de
 * produção — nesse caso, mesmo que as locations o refiram, Flyway não
 * encontra nada e (com {@code fail-on-missing-locations=false}, omissão do
 * Spring Boot) segue em frente sem aplicar nada. Esta camada 3 protege o
 * caso que a 1 não cobre: correr a partir de {@code target/classes} (IDE,
 * {@code mvn spring-boot:run}) ou de um artefacto construído com o perfil
 * de <em>build</em> de desenvolvimento — exatamente onde o engano de
 * configuração é mais provável (<em>preview</em>/<em>staging</em>).
 *
 * <p>Dono: {@code backend-platform} (ADR-0013 D3, camada 3).
 */
@Component
class SeedLocationsGuard implements FlywayConfigurationCustomizer {

    private static final String SEED_LOCATION_PATH = "db/seed";
    private static final Profiles DEV_OR_LOCAL = Profiles.of("local", "dev");
    private static final Profiles PROD = Profiles.of("prod");

    private final Environment environment;

    SeedLocationsGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void customize(FluentConfiguration configuration) {
        boolean seedLocationPresent = Arrays.stream(configuration.getLocations())
                .anyMatch(SeedLocationsGuard::referencesSeed);
        if (!seedLocationPresent) {
            return;
        }

        boolean devOrLocalActive = environment.acceptsProfiles(DEV_OR_LOCAL);
        boolean prodActive = environment.acceptsProfiles(PROD);
        if (!devOrLocalActive || prodActive) {
            throw new IllegalStateException(
                    "ADR-0013 (fronteira de segurança, não conveniência): as locations "
                            + "efetivas do Flyway incluem '" + SEED_LOCATION_PATH + "' (dados de "
                            + "demonstração dev-only) mas os perfis ativos são "
                            + Arrays.toString(environment.getActiveProfiles()) + " — nem 'local' nem "
                            + "'dev' isolados, sem 'prod'. Isto acontece tipicamente quando "
                            + "SPRING_FLYWAY_LOCATIONS foi herdado de outro ambiente (a variável de "
                            + "ambiente sobrepõe-se a qualquer application*.yml deste repositório) ou "
                            + "quando 'dev' foi ativado ao lado de 'prod' por engano. O arranque é "
                            + "abortado deliberadamente: aplicar este seed é irreversível sem "
                            + "restauro e mete prestadores, pedidos e avaliações fictícios numa base "
                            + "de dados real.");
        }
    }

    private static boolean referencesSeed(Location location) {
        String path = location.getPath();
        return SEED_LOCATION_PATH.equals(path) || path.startsWith(SEED_LOCATION_PATH + "/");
    }
}
