package pt.servimatch.modules.requests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova contra PostGIS real ({@link SharedPostgis}) de {@code GET
 * /v1/requests} (Gap #6 fechado nesta onda — ver
 * {@code web/site/src/services/http/requestsService.ts}): isolamento entre
 * clientes (o filtro de dono é sempre em SQL, nunca em memória — CLAUDE.md)
 * e {@code status} inválido devolvendo {@code 400}, nunca uma página vazia
 * silenciosa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class ListMyRequestsIntegrationTest {

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
    void customerANeverSeesCustomerBsRequests() throws Exception {
        UUID categoryId = insertCategory();
        String customerASub = "kc-mine-it-cust-a-" + UUID.randomUUID();
        UUID customerAId = insertUser(customerASub);
        String customerBSub = "kc-mine-it-cust-b-" + UUID.randomUUID();
        UUID customerBId = insertUser(customerBSub);

        UUID requestAId = insertRequest(customerAId, categoryId, "DRAFT");
        UUID requestBId = insertRequest(customerBId, categoryId, "DRAFT");

        mockMvc.perform(get("/v1/requests").with(customerJwt(customerASub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + requestAId + "')]").exists())
                .andExpect(jsonPath("$.items[?(@.id=='" + requestBId + "')]").doesNotExist());

        mockMvc.perform(get("/v1/requests").with(customerJwt(customerBSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + requestBId + "')]").exists())
                .andExpect(jsonPath("$.items[?(@.id=='" + requestAId + "')]").doesNotExist());
    }

    @Test
    void invalidStatusFilterIsBadRequestNotAnEmptyPage() throws Exception {
        String customerSub = "kc-mine-it-cust-badstatus-" + UUID.randomUUID();

        mockMvc.perform(get("/v1/requests").param("status", "NOT_A_REAL_STATUS").with(customerJwt(customerSub)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/validation"));
    }

    @Test
    void listMineReturnsExactAddressToTheOwner() throws Exception {
        UUID categoryId = insertCategory();
        String customerSub = "kc-mine-it-cust-address-" + UUID.randomUUID();
        UUID customerId = insertUser(customerSub);
        insertRequest(customerId, categoryId, "DRAFT");

        mockMvc.perform(get("/v1/requests").with(customerJwt(customerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].address.line1").value("Rua Teste"))
                .andExpect(jsonPath("$.items[0].address.postalCode").value("1000-001"));
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

    private UUID insertUser(String keycloakSub) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO users (id, keycloak_sub, email, display_name)
                        VALUES (:id, :sub, :email, 'Utilizador de teste')
                        """)
                .param("id", id)
                .param("sub", keycloakSub)
                .param("email", keycloakSub + "@example.test")
                .update();
        return id;
    }

    private UUID insertRequest(UUID customerId, UUID categoryId, String status) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO service_request (
                            id, customer_id, category_id, title, address_line1, address_postal_code,
                            address_city, address_country, status
                        ) VALUES (
                            :id, :customerId, :categoryId, 'Fuga de água na cozinha', 'Rua Teste', '1000-001',
                            'Lisboa', 'PT', :status
                        )
                        """)
                .param("id", id)
                .param("customerId", customerId)
                .param("categoryId", categoryId)
                .param("status", status)
                .update();
        return id;
    }
}
