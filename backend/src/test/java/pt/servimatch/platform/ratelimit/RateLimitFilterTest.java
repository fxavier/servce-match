package pt.servimatch.platform.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.config.IdempotencyConfig;
import pt.servimatch.config.RateLimitConfig;
import pt.servimatch.config.SecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RateLimitFilter} com {@code servimatch.rate-limit.trusted-proxies}
 * na omissão (vazio): reproduz o achado 3 da auditoria de segurança —
 * {@code X-Forwarded-For} forjado, de qualquer origem, contornava o limite
 * global. Cada método de teste usa um {@code remoteAddr} próprio para não
 * partilhar chave de bucket com os restantes (o contexto Spring, e por isso
 * o {@link InMemoryBucketResolver}, é reutilizado entre métodos).
 *
 * <p>Mesmo padrão de contexto mínimo que {@code SecurityConfigTest}: carrega
 * apenas {@link SecurityConfig} e as configurações de que depende, com um
 * controlador descartável nos dois caminhos públicos relevantes
 * ({@code /v1/categories} e {@code /v1/webhooks/payments/**}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = RateLimitFilterTest.TestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "servimatch.rate-limit.capacity=3",
        "servimatch.rate-limit.refill-period=PT1M",
        "servimatch.rate-limit.webhook.capacity=2",
        "servimatch.rate-limit.webhook.refill-period=PT1M"
        // trusted-proxies deliberadamente omisso: prova o comportamento por omissão.
})
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mainPath_requestsUnderCapacityAreAllowed() throws Exception {
        RequestPostProcessor client = remoteAddr("203.0.113.20");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/v1/categories").with(client))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "3"));
        }
    }

    @Test
    void errorPath_exceedingCapacityReturns429ProblemDetails() throws Exception {
        RequestPostProcessor client = remoteAddr("203.0.113.30");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/v1/categories").with(client)).andExpect(status().isOk());
        }

        mockMvc.perform(get("/v1/categories").with(client))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/rate-limited"))
                .andExpect(header().exists("Retry-After"));
    }

    /**
     * Achado 3 (auditoria de segurança): antes da correção, um
     * {@code X-Forwarded-For} rotativo criava uma chave de bucket nova a
     * cada pedido — 200 pedidos com o cabeçalho rotativo produziam 0×429.
     * Sem proxy de confiança configurado, o cabeçalho tem de ser ignorado:
     * os quatro pedidos abaixo, todos da mesma ligação TCP mas cada um com
     * um {@code X-Forwarded-For} diferente, têm de bater no mesmo limite de
     * capacidade 3 como se o cabeçalho não existisse.
     */
    @Test
    void forgedXForwardedForHeaderDoesNotBypassLimit_whenNoTrustedProxyConfigured() throws Exception {
        String remoteAddr = "203.0.113.10";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/v1/categories")
                            .with(remoteAddr(remoteAddr))
                            .header("X-Forwarded-For", "198.51.100." + i))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/v1/categories")
                        .with(remoteAddr(remoteAddr))
                        .header("X-Forwarded-For", "198.51.100.99"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/rate-limited"));
    }

    /**
     * O limite dedicado ao webhook (capacidade 2) é mais apertado do que o
     * global (capacidade 3) — tem de rejeitar antes de o global o faria.
     */
    @Test
    void webhookPath_hasDedicatedStricterLimitThanGlobal() throws Exception {
        RequestPostProcessor client = remoteAddr("203.0.113.40");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/v1/webhooks/payments/testgw").with(client))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "2"));
        }

        mockMvc.perform(post("/v1/webhooks/payments/testgw").with(client))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/rate-limited"));
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Configuration
    @EnableAutoConfiguration(excludeName = "org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration")
    @Import({SecurityConfig.class, RateLimitConfig.class, IdempotencyConfig.class})
    static class TestConfig {

        @RestController
        static class ProbeController {

            @GetMapping("/v1/categories")
            String categories() {
                return "ok";
            }

            @PostMapping("/v1/webhooks/payments/{gateway}")
            String webhook(@PathVariable String gateway) {
                return "ok";
            }
        }
    }
}
