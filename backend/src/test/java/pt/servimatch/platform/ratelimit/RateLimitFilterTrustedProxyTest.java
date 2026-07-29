package pt.servimatch.platform.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.config.IdempotencyConfig;
import pt.servimatch.config.RateLimitConfig;
import pt.servimatch.config.SecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RateLimitFilter} com {@code servimatch.rate-limit.trusted-proxies}
 * explicitamente configurado a incluir a ligação TCP usada pelo
 * {@code MockMvc} ({@code 127.0.0.1}). Prova que a correção do achado 3 não
 * é "desligar {@code X-Forwarded-For}" — é honrá-lo apenas quando declarado
 * de confiança, continuando a distinguir clientes reais atrás desse proxy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = RateLimitFilterTrustedProxyTest.TestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "servimatch.rate-limit.capacity=2",
        "servimatch.rate-limit.refill-period=PT1M",
        "servimatch.rate-limit.trusted-proxies=127.0.0.1/32"
})
class RateLimitFilterTrustedProxyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void distinctForwardedClientsBehindTrustedProxyGetIndependentBuckets() throws Exception {
        // MockMvc liga sempre a partir de 127.0.0.1, o proxy de confiança
        // configurado acima. Três "clientes" diferentes (X-Forwarded-For
        // distinto), um pedido cada, apesar de a capacidade global ser 2 —
        // só funciona se cada um tiver a sua própria chave de bucket.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/v1/categories").header("X-Forwarded-For", "198.51.100." + i))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void sameForwardedClientBehindTrustedProxyIsStillRateLimited() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/v1/categories").header("X-Forwarded-For", "198.51.100.50"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/v1/categories").header("X-Forwarded-For", "198.51.100.50"))
                .andExpect(status().isTooManyRequests());
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
        }
    }
}
