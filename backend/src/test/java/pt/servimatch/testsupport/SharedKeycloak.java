package pt.servimatch.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.Base64;

/**
 * Contentor Keycloak partilhado (padrão "singleton container", ver
 * {@link SharedPostgis}/{@code pt.servimatch.modules.uploads.SharedMinio})
 * para os testes que exercitam a cadeia de autenticação <b>real</b> —
 * assinatura JWT, {@code iss}, {@code aud} e conversão de roles do realm
 * ({@code KeycloakRoleConverter}) — em vez de um {@code Authentication}
 * sintético via {@code spring-security-test} (skill
 * {@code testcontainers-integration-test}: "Keycloak real para o mecanismo,
 * mock para a combinatória").
 *
 * <p>Importa {@code src/test/resources/keycloak/realm-servimatch.json},
 * cópia deliberada (não uma referência de caminho relativo, frágil ao
 * diretório de trabalho do Maven) de {@code infra/keycloak/realm-servimatch.json}
 * (agente {@code platform-infra}). {@link RealmFileSyncTest} verifica que as
 * duas cópias não divergem — se falhar, resincroniza esta cópia a partir da
 * de {@code infra/}; nunca o inverso (não é âmbito de escrita deste agente).
 *
 * <p>Modo {@code start-dev} com base de dados de desenvolvimento embutida
 * (sem Postgres): mais rápido a arrancar do que replicar o
 * {@code docker-compose.yml} local, e não precisamos de persistência entre
 * execuções.
 *
 * <p><b>Prazo de arranque (diagnóstico de incidente, ver relatório de
 * entrega desta onda):</b> a imagem oficial do Keycloak não vem
 * pré-otimizada para as opções usadas aqui — cada contentor novo paga uma
 * "augmentação" Quarkus (medida entre 49s e 86s numa máquina sob carga
 * pesada nesta sessão) antes sequer de inicializar o esquema/importar o
 * realm. Com {@code 120s} de prazo isso é marginal mesmo em condições
 * normais (medido {@code 1m49s} de arranque total numa execução) e falha de
 * forma consistente sob contenção de recursos do host (carga do Docker
 * Desktop, outros processos) — não por regressão de código; confirmado ao
 * reproduzir a falha em {@code main} com {@code load average} do host acima
 * de 20 e ao ver, no mesmo log, o contentor a completar o arranque e a
 * começar a importar o realm depois do prazo anterior já ter expirado.
 * {@code 5} minutos dá margem real para hardware de CI desconhecido, sem
 * mascarar uma falha genuína (se ultrapassar isto, é mesmo um problema).
 * {@code withReuse(true)} amortiza o custo entre execuções locais
 * consecutivas (não tem efeito em CI, onde cada corrida é uma máquina nova,
 * a menos que {@code testcontainers.reuse.enable=true} esteja ativo — opt-in
 * do próprio Testcontainers, nunca automático).
 */
public final class SharedKeycloak {

    public static final String REALM = "servimatch";
    /** Cliente público com ROPC ativo — "APENAS dev/CI" (ver realm-servimatch.json). */
    public static final String TEST_CLIENT_ID = "servimatch-local-test";
    public static final String CUSTOMER_USERNAME = "customer.test@servimatch.pt";
    public static final String PROVIDER_USERNAME = "provider.test@servimatch.pt";
    public static final String ADMIN_USERNAME = "admin.test@servimatch.pt";
    public static final String SEEDED_PASSWORD = "DevLocal#2026";

    private static final GenericContainer<?> CONTAINER = new GenericContainer<>(
            DockerImageName.parse("quay.io/keycloak/keycloak:26.7.0"))
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("keycloak/realm-servimatch.json"),
                    "/opt/keycloak/data/import/realm-servimatch.json")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KC_HOSTNAME_STRICT", "false")
            .withEnv("KC_HTTP_ENABLED", "true")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .withReuse(true)
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forPort(8080)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(5)));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Se uma tentativa de arranque já falhou nesta JVM, guarda a causa e
     * falha imediatamente nas chamadas seguintes em vez de repetir uma
     * espera de {@code 5} minutos já sabida como condenada — sem isto, três
     * métodos {@code @Test} que chamem {@link #accessToken} (ex.
     * {@code SharedKeycloakSmokeTest}, um por utilizador semeado) multiplicam
     * o prazo de arranque por três antes de o build reportar falha (medido:
     * ~6m54s em vez de ~2 min para o primeiro diagnóstico), o que já
     * aconteceu no incidente que motivou este comentário.
     */
    private static volatile RuntimeException startupFailure;

    private SharedKeycloak() {
    }

    public static synchronized String issuerUri() {
        ensureStarted();
        return baseUrl() + "/realms/" + REALM;
    }

    public static synchronized String jwkSetUri() {
        ensureStarted();
        return baseUrl() + "/realms/" + REALM + "/protocol/openid-connect/certs";
    }

    private static String baseUrl() {
        return "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(8080);
    }

    private static void ensureStarted() {
        if (startupFailure != null) {
            throw startupFailure;
        }
        if (!CONTAINER.isRunning()) {
            try {
                CONTAINER.start();
            } catch (RuntimeException e) {
                startupFailure = new IllegalStateException(
                        "Contentor Keycloak não arrancou (ver causa) — falha em cache para esta JVM, "
                                + "não repete a espera completa noutras chamadas.", e);
                throw startupFailure;
            }
        }
    }

    /** Obtém um access token real por Resource Owner Password Credentials (grant direto, cliente {@link #TEST_CLIENT_ID}). */
    public static String accessToken(String username, String password) {
        ensureStarted();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", TEST_CLIENT_ID);
        form.add("username", username);
        form.add("password", password);

        RestClient client = RestClient.create();
        String response = client.post()
                .uri(baseUrl() + "/realms/" + REALM + "/protocol/openid-connect/token")
                .headers(h -> h.setContentType(MediaType.APPLICATION_FORM_URLENCODED))
                .body(form)
                .retrieve()
                .body(String.class);
        try {
            JsonNode node = MAPPER.readTree(response);
            return node.get("access_token").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Falha a obter token de " + username + ": " + response, e);
        }
    }

    /** Decodifica o corpo (payload) de um JWT sem validar assinatura — só para os testes lerem claims (ex. {@code sub}). */
    public static JsonNode decodePayload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return MAPPER.readTree(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Token não é um JWT decodificável: " + jwt, e);
        }
    }
}
