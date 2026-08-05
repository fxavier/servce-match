package pt.servimatch.platform.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.config.IdempotencyConfig;
import pt.servimatch.config.RateLimitConfig;
import pt.servimatch.config.SecurityConfig;
import pt.servimatch.platform.error.ProblemDetailsResponseWriter;
import pt.servimatch.platform.error.ProblemType;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão do achado M3 da auditoria de segurança (Onda C):
 * {@link IdempotencyFilter} não pode isolar a chave de idempotência por um
 * literal {@code "anonymous"} partilhado por todos os pedidos não
 * autenticados. Um atacante sem token podia semear uma
 * {@code Idempotency-Key} conhecida com uma resposta de erro contra um
 * endpoint público de escrita (ex. {@code POST /v1/webhooks/payments/**}) e,
 * ao repetir-se a mesma chave num pedido legítimo, este seria bloqueado com
 * {@code 409 idempotency-key-conflict} em vez de processado — um evento de
 * pagamento assinado corretamente nunca chegaria a ser persistido.
 *
 * <p>Reproduz a cadeia completa descrita pelo auditor contra um controlador
 * descartável que simula o comportamento do webhook (permitAll, verificação
 * de "assinatura" a partir de um cabeçalho, 401 se inválida) — não contra o
 * módulo {@code payments} em si, que não é âmbito deste agente.
 *
 * <p>Confirma também, no mesmo teste, que o comportamento normal de
 * idempotência (replay em repetição com o mesmo corpo, 409 em conflito) se
 * mantém intacto para pedidos com principal autenticado — a correção exclui
 * apenas quem não tem autenticação real, não estreita o filtro em geral.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = IdempotencyFilterAuthenticationScopeTest.TestConfig.class)
@AutoConfigureMockMvc
class IdempotencyFilterAuthenticationScopeTest {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestConfig.ProbeWebhookController probeWebhookController;

    @Configuration
    // Mesma justificação que em SecurityConfigTest: evita o scan completo de
    // pt.servimatch (e os @Repository dos módulos de domínio) — só interessa
    // aqui o comportamento do SecurityFilterChain/IdempotencyFilter em si.
    @EnableAutoConfiguration(excludeName = "org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration")
    @Import({SecurityConfig.class, RateLimitConfig.class, IdempotencyConfig.class})
    static class TestConfig {

        @RestController
        static class ProbeWebhookController {

            private final AtomicInteger processedCount = new AtomicInteger();

            /**
             * Casa com o padrão {@code permitAll()} de
             * {@code /v1/webhooks/payments/**} em {@code SecurityConfig},
             * exatamente como o webhook de pagamentos real: sem
             * autenticação, "assinatura" verificada a partir de um
             * cabeçalho simples.
             */
            @PostMapping("/v1/webhooks/payments/probe")
            public void handle(
                    @RequestHeader(value = "X-Probe-Signature", required = false) String signature,
                    @RequestBody(required = false) String body,
                    HttpServletResponse response,
                    ObjectMapper objectMapper) throws IOException {
                if (!"valid".equals(signature)) {
                    ProblemDetailsResponseWriter.write(
                            response,
                            objectMapper,
                            HttpStatus.UNAUTHORIZED,
                            ProblemType.UNAUTHENTICATED,
                            "Assinatura inválida",
                            "A assinatura do evento não pôde ser verificada.");
                    return;
                }
                processedCount.incrementAndGet();
                response.setStatus(200);
            }

            int processedCount() {
                return processedCount.get();
            }
        }

        @RestController
        static class ProbeAuthenticatedController {

            private final AtomicInteger processedCount = new AtomicInteger();

            @PostMapping("/v1/_probe/authenticated-write")
            public String handle(@RequestBody(required = false) String body) {
                processedCount.incrementAndGet();
                return "processed-" + processedCount.get();
            }
        }
    }

    /**
     * Cadeia de exploração completa reportada pelo auditor: pedido anónimo
     * com {@code Idempotency-Key} conhecida contra o webhook envenena a
     * chave com um {@code 401}; o pedido legítimo com a mesma chave, mas
     * assinatura válida, tem de ser processado — nunca bloqueado por
     * conflito de idempotência.
     */
    @Test
    void anonymousRequestNeverPoisonsIdempotencyKeyForALaterAuthenticSignedWebhookCall() throws Exception {
        String poisonKey = "poison-key-001";

        mockMvc.perform(post("/v1/webhooks/payments/probe")
                        .header(IDEMPOTENCY_KEY_HEADER, poisonKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"garbage\":true}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/webhooks/payments/probe")
                        .header(IDEMPOTENCY_KEY_HEADER, poisonKey)
                        .header("X-Probe-Signature", "valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"real\":\"event\"}"))
                .andExpect(status().isOk());

        Assertions.assertEquals(
                1,
                probeWebhookController.processedCount(),
                "o pedido assinado corretamente tem de ser processado, não silenciado por um conflito de idempotência semeado por um pedido anónimo");
    }

    @Test
    void authenticatedRequestsStillGetIdempotentReplayOnSameBody() throws Exception {
        String key = "authenticated-key-001";

        mockMvc.perform(post("/v1/_probe/authenticated-write")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .header(IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"same\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().string("processed-1"));

        // Repetição com o mesmo corpo: resposta reproduzida do cache, sem
        // reinvocar o controlador (continua "processed-1").
        mockMvc.perform(post("/v1/_probe/authenticated-write")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .header(IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"same\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().string("processed-1"));
    }

    @Test
    void authenticatedRequestsStillGet409OnConflictingBody() throws Exception {
        String key = "authenticated-key-002";

        mockMvc.perform(post("/v1/_probe/authenticated-write")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .header(IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"first\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/_probe/authenticated-write")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .header(IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"second\":true}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/idempotency-key-conflict"));
    }
}
