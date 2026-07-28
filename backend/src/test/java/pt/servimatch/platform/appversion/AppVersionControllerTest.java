package pt.servimatch.platform.appversion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pt.servimatch.config.IdempotencyConfig;
import pt.servimatch.config.RateLimitConfig;
import pt.servimatch.config.SecurityConfig;
import pt.servimatch.platform.error.GlobalExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /v1/app/version-status}: caminho principal (as três decisões,
 * a partir de configuração — sem tabela) e caso de erro (versão mal
 * formada → 400). Mesmo padrão de contexto mínimo que
 * {@code pt.servimatch.config.SecurityConfigTest} — carrega
 * {@link SecurityConfig} (é público, ver {@code PUBLIC_GET_ENDPOINTS}) e
 * este controlador, não a aplicação completa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = AppVersionControllerTest.TestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "servimatch.app-version.platforms.ios.min-supported-version=1.0.0",
        "servimatch.app-version.platforms.ios.latest-version=2.0.0",
        "servimatch.app-version.platforms.ios.store-url=https://apps.apple.com/pt/app/servimatch",
        "servimatch.app-version.platforms.android.min-supported-version=1.0.0",
        "servimatch.app-version.platforms.android.latest-version=2.0.0"
})
class AppVersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void belowMinSupportedVersionRequiresUpdate() throws Exception {
        mockMvc.perform(get("/v1/app/version-status").param("platform", "IOS").param("appVersion", "0.9.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPDATE_REQUIRED"))
                .andExpect(jsonPath("$.minSupportedVersion").value("1.0.0"))
                .andExpect(jsonPath("$.storeUrl").value("https://apps.apple.com/pt/app/servimatch"));
    }

    @Test
    void betweenMinAndLatestRecommendsUpdate() throws Exception {
        mockMvc.perform(get("/v1/app/version-status").param("platform", "IOS").param("appVersion", "1.4.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPDATE_RECOMMENDED"));
    }

    @Test
    void atOrAboveLatestIsOk() throws Exception {
        mockMvc.perform(get("/v1/app/version-status").param("platform", "ANDROID").param("appVersion", "2.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void endpointIsPublicNoTokenRequired() throws Exception {
        mockMvc.perform(get("/v1/app/version-status").param("platform", "WEB").param("appVersion", "1.0.0"))
                .andExpect(status().isOk());
    }

    @Test
    void malformedVersionIsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/app/version-status").param("platform", "IOS").param("appVersion", "not-a-version"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/validation"));
    }

    @Test
    void missingPlatformIsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/app/version-status").param("appVersion", "1.0.0"))
                .andExpect(status().isBadRequest());
    }

    @Configuration
    @EnableAutoConfiguration(excludeName = "org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration")
    @Import({SecurityConfig.class, RateLimitConfig.class, IdempotencyConfig.class, GlobalExceptionHandler.class, AppVersionController.class})
    static class TestConfig {
    }
}
