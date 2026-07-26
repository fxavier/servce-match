package pt.servimatch.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobertura mínima da configuração de segurança transversal (ADR-0002/0009):
 * sem token é 401, com role errada é 403, com role certa é 200 — sempre em
 * formato RFC 9457 quando aplicável.
 *
 * <p>Carrega deliberadamente <b>apenas</b> {@link SecurityConfig} e as duas
 * configurações de que depende ({@link RateLimitConfig},
 * {@link IdempotencyConfig}), mais autoconfiguração do Spring Boot — nunca
 * {@code ServiMatchApplication}. Isto evita o scan de {@code pt.servimatch}
 * completo (e, com ele, dos {@code @Repository} dos módulos de domínio, que
 * precisam de um {@code JdbcClient}/{@code DataSource} que este slice não
 * fornece nem deve fornecer: o que se verifica aqui é o comportamento do
 * {@code SecurityFilterChain} em si, contra um controller descartável, não
 * uma rota de negócio nem persistência). Módulos de domínio validam a sua
 * própria integração com segurança nos respetivos testes, com Testcontainers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = SecurityConfigTest.TestConfig.class)
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Configuration
    // spring-modulith-starter-test traz o runtime do Modulith para o
    // classpath de teste, cuja autoconfiguração exige uma classe anotada
    // @SpringBootApplication (ServiMatchApplication) para descobrir os
    // módulos — irrelevante aqui, e é precisamente o que este teste evita
    // arrancar. A verificação de fronteiras de módulo tem o seu próprio
    // teste (pt.servimatch.ModularityTests), que usa ServiMatchApplication
    // a sério.
    @EnableAutoConfiguration(excludeName = "org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration")
    @Import({SecurityConfig.class, RateLimitConfig.class, IdempotencyConfig.class})
    static class TestConfig {

        @RestController
        static class ProbeController {

            @GetMapping("/v1/_probe/customer-only")
            @PreAuthorize("hasRole('CUSTOMER')")
            public String probe() {
                return "ok";
            }
        }
    }

    @Test
    void publicEndpointIsAccessibleWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401ProblemDetails() throws Exception {
        mockMvc.perform(get("/v1/_probe/customer-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/unauthenticated"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void protectedEndpointWithWrongRoleReturns403ProblemDetails() throws Exception {
        mockMvc.perform(get("/v1/_probe/customer-only")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/forbidden"));
    }

    @Test
    void protectedEndpointWithCorrectRoleReturns200() throws Exception {
        mockMvc.perform(get("/v1/_probe/customer-only")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
    }
}
