package pt.servimatch.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Achado 7 (auditoria de segurança): {@code application.yml} tem defaults de
 * desenvolvimento para {@code spring.datasource.password} e
 * {@code servimatch.uploads.secret-key} — uma variável de ambiente em falta
 * não impede o arranque, liga apenas com uma credencial pública e conhecida
 * deste repositório. O perfil {@code prod} ({@code application-prod.yml})
 * remove esses defaults, e {@link ProductionSecretsRequired} torna essa
 * ausência fatal ao arranque (o binding de {@code @ConfigurationProperties}
 * usado por {@code DataSourceProperties}/{@code UploadsProperties}, por si
 * só, deixaria antes o campo com o literal {@code "${VAR}"} em silêncio —
 * ver o próprio javadoc de {@link ProductionSecretsRequired}).
 *
 * <p>Carrega o {@link org.springframework.context.ApplicationContext} real a
 * partir de {@code application.yml}/{@code application-prod.yml} via
 * {@link ConfigDataApplicationContextInitializer}, com
 * {@link ProductionSecretsRequired} como única configuração — não precisa de
 * base de dados nem object storage a sério, só do bean de validação.
 */
class ProductionProfileSecretsTest {

    // PropertyPlaceholderAutoConfiguration regista o resolvedor de
    // "${...}" que @Value usa (o mesmo que @SpringBootApplication traz
    // automaticamente na aplicação real via auto-configuração) — sem ele,
    // este contexto mínimo nem tentaria resolver os placeholders.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(ProductionSecretsRequired.class);

    @Test
    void defaultProfileStartsWithDevDefaultsAndNoSecretsProvided() {
        // Fora do perfil "prod", ProductionSecretsRequired nem sequer é
        // ativado (@Profile("prod")) — os defaults de desenvolvimento
        // continuam a servir localmente/em teste.
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void prodProfileFailsFastWhenDbPasswordIsMissing() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod", "UPLOADS_S3_SECRET_KEY=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodProfileFailsFastWhenUploadsSecretKeyIsMissing() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod", "DB_PASSWORD=a-real-production-secret")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodProfileStartsWhenBothSecretsAreExplicitlyProvided() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "DB_PASSWORD=a-real-production-secret",
                        // Vazia mas DEFINIDA continua válido: sinal para a cadeia de
                        // credenciais IAM/role (ver UploadsStorageConfig).
                        "UPLOADS_S3_SECRET_KEY=")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
