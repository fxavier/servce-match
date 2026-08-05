package pt.servimatch.modules.requests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pt.servimatch.testsupport.SharedPostgis;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova contra PostGIS real ({@link SharedPostgis}) do achado M5 da
 * auditoria de segurança (Onda C): {@code POST /v1/requests} com
 * {@code urgency} fora do enum, ou com um campo de {@code address} acima do
 * limite {@code VARCHAR}/{@code CHAR} da tabela {@code service_request}
 * (V7), tinha de devolver {@code 400}/{@code 422} — e devolvia {@code 409
 * "Conflito de estado"}, porque o valor atravessava o bean validation e só o
 * {@code CHECK}/limite de coluna da base de dados o travava. O caso do
 * {@code 409} é o comportamento observado antes desta correção; estes testes
 * provam que já não acontece.
 *
 * <p>Não cobre {@code address.regionCode} contra o catálogo de regiões
 * (bloqueado — ver relatório de entrega: {@code RegionCatalog} é {@code
 * internal} de {@code providers}, sem API pública equivalente à validação já
 * aplicada em {@code PUT /v1/providers/me}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class CreateServiceRequestValidationIntegrationTest {

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

    /**
     * Antes da correção: {@code urgency} era {@code String} sem
     * {@code @Pattern}/enum; {@code "FLEXIBLE"} atravessava o bean
     * validation, chegava ao {@code INSERT} e o {@code CHECK
     * chk_service_request_urgency} (V7) travava-o só na base de dados — o
     * {@code GlobalExceptionHandler} traduzia essa
     * {@code DataIntegrityViolationException} em {@code 409}. Agora
     * {@code urgency} é {@link UrgencyLevel}: um valor fora do enum falha a
     * desserialização Jackson, {@code 400}, sem chegar à base de dados.
     */
    @Test
    void urgencyOutsideTheEnumIsBadRequestNotAConflict() throws Exception {
        UUID categoryId = insertCategory();
        String customerSub = "kc-create-req-urgency-" + UUID.randomUUID();

        String body = """
                {
                  "categoryId": "%s",
                  "title": "Fuga de água na cozinha",
                  "address": { "line1": "Rua Teste", "postalCode": "1000-001", "city": "Lisboa", "country": "PT" },
                  "urgency": "FLEXIBLE"
                }
                """.formatted(categoryId);

        mockMvc.perform(post("/v1/requests")
                        .with(customerJwt(customerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/validation"));
    }

    /**
     * {@code address.postalCode VARCHAR(20)} (V7): um valor acima do limite
     * tinha o mesmo destino que {@code urgency} — {@code 409} em vez de
     * {@code 400}. Agora rejeitado pelo {@code @Size} de {@code AddressDto},
     * antes de qualquer escrita.
     */
    @Test
    void addressFieldAboveTheColumnLimitIsBadRequestNotAConflict() throws Exception {
        UUID categoryId = insertCategory();
        String customerSub = "kc-create-req-address-" + UUID.randomUUID();
        String postalCodeTooLong = "1".repeat(21);

        String body = """
                {
                  "categoryId": "%s",
                  "title": "Fuga de água na cozinha",
                  "address": { "line1": "Rua Teste", "postalCode": "%s", "city": "Lisboa", "country": "PT" }
                }
                """.formatted(categoryId, postalCodeTooLong);

        mockMvc.perform(post("/v1/requests")
                        .with(customerJwt(customerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/validation"));
    }

    /** Controlo positivo: dentro dos limites, o pedido é criado normalmente. */
    @Test
    void validRequestIsCreated() throws Exception {
        UUID categoryId = insertCategory();
        String customerSub = "kc-create-req-ok-" + UUID.randomUUID();

        String body = """
                {
                  "categoryId": "%s",
                  "title": "Fuga de água na cozinha",
                  "address": { "line1": "Rua Teste", "postalCode": "1000-001", "city": "Lisboa", "country": "PT" },
                  "urgency": "HIGH"
                }
                """.formatted(categoryId);

        mockMvc.perform(post("/v1/requests")
                        .with(customerJwt(customerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.urgency").value("HIGH"));
    }

    private static RequestPostProcessor customerJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private UUID insertCategory() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO category (id, slug, name) VALUES (:id, :slug, 'Categoria Teste')")
                .param("id", id)
                .param("slug", "categoria-teste-" + id)
                .update();
        return id;
    }
}
