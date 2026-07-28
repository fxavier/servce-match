package pt.servimatch.modules.notifications;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pt.servimatch.testsupport.SharedPostgis;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração HTTP de {@code POST}/{@code DELETE /v1/device-tokens}
 * contra Postgres real ({@link SharedPostgis}) — inclui os dois casos de
 * erro assinalados na tarefa (CLAUDE.md §5): registar o mesmo token duas
 * vezes (o {@code UNIQUE (token)} da V5 tem de ser respeitado sem 500) e
 * apagar um token que não é do utilizador autenticado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("notifications-it")
class DeviceTokenControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgis::jdbcUrl);
        registry.add("spring.datasource.username", SharedPostgis::username);
        registry.add("spring.datasource.password", SharedPostgis::password);
    }

    @Test
    void registerDeviceTokenWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/v1/device-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"tok-anon","platform":"ANDROID"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerThenDeleteOwnTokenRoundTrips() throws Exception {
        String subject = "kc-notifications-it-" + UUID.randomUUID();
        String token = "tok-" + UUID.randomUUID();

        registerToken(subject, token, "IOS", "1.4.2").andExpect(status().isNoContent());

        UUID userId = userIdFor(subject);
        assertThat(platformOf(token)).isEqualTo("IOS");
        assertThat(userIdOf(token)).isEqualTo(userId);

        mockMvc.perform(delete("/v1/device-tokens/{token}", token)
                        .with(jwt().jwt(builder -> builder.subject(subject))))
                .andExpect(status().isNoContent());

        assertThat(countByToken(token)).isZero();
    }

    @Test
    void registeringSameTokenTwiceIsIdempotentNotAServerError() throws Exception {
        String subject = "kc-notifications-it-" + UUID.randomUUID();
        String token = "tok-" + UUID.randomUUID();

        registerToken(subject, token, "ANDROID", "1.0.0").andExpect(status().isNoContent());
        // Repetir o registo do mesmo token (UNIQUE (token), V5) não pode
        // produzir 500 nem duplicar a linha — upsert por token.
        registerToken(subject, token, "ANDROID", "1.1.0").andExpect(status().isNoContent());

        assertThat(countByToken(token)).isEqualTo(1L);
        assertThat(jdbcClient.sql("SELECT app_version FROM device_token WHERE token = :token")
                        .param("token", token)
                        .query(String.class)
                        .single())
                .isEqualTo("1.1.0");
    }

    @Test
    void deletingTokenOfAnotherUserReturns404AndDoesNotDeleteIt() throws Exception {
        String owner = "kc-notifications-it-" + UUID.randomUUID();
        String intruder = "kc-notifications-it-" + UUID.randomUUID();
        String token = "tok-" + UUID.randomUUID();

        registerToken(owner, token, "WEB", null).andExpect(status().isNoContent());

        mockMvc.perform(delete("/v1/device-tokens/{token}", token)
                        .with(jwt().jwt(builder -> builder.subject(intruder))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/not-found"));

        assertThat(countByToken(token)).isEqualTo(1L);
    }

    @Test
    void deletingUnknownTokenReturns404() throws Exception {
        String subject = "kc-notifications-it-" + UUID.randomUUID();

        mockMvc.perform(delete("/v1/device-tokens/{token}", "tok-does-not-exist-" + UUID.randomUUID())
                        .with(jwt().jwt(builder -> builder.subject(subject))))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions registerToken(String subject, String token, String platform, String appVersion) throws Exception {
        String appVersionJson = appVersion == null ? "" : ",\"appVersion\":\"" + appVersion + "\"";
        return mockMvc.perform(post("/v1/device-tokens")
                .with(jwt().jwt(builder -> builder.subject(subject)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"platform\":\"" + platform + "\"" + appVersionJson + "}"));
    }

    private UUID userIdFor(String subject) {
        return jdbcClient.sql("SELECT id FROM users WHERE keycloak_sub = :sub")
                .param("sub", subject)
                .query(UUID.class)
                .single();
    }

    private String platformOf(String token) {
        return jdbcClient.sql("SELECT platform FROM device_token WHERE token = :token")
                .param("token", token)
                .query(String.class)
                .single();
    }

    private UUID userIdOf(String token) {
        return jdbcClient.sql("SELECT user_id FROM device_token WHERE token = :token")
                .param("token", token)
                .query(UUID.class)
                .single();
    }

    private long countByToken(String token) {
        return jdbcClient.sql("SELECT count(*) FROM device_token WHERE token = :token")
                .param("token", token)
                .query(Long.class)
                .single();
    }
}
