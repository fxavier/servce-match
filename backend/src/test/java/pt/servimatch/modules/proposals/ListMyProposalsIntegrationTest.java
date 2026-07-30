package pt.servimatch.modules.proposals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import pt.servimatch.testsupport.TestDatabase;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova contra PostGIS real ({@link SharedPostgis}) de {@code GET
 * /v1/proposals/me} (Gap #7 fechado nesta onda — ver
 * {@code web/site/src/services/http/proposalsService.ts}): isolamento entre
 * prestadores (filtro de dono sempre em SQL) e a decisão de "sem perfil de
 * prestador → página vazia, não 403" (ver javadoc de
 * {@code ProposalsService#listMine}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class ListMyProposalsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgis::jdbcUrl);
        registry.add("spring.datasource.username", SharedPostgis::username);
        registry.add("spring.datasource.password", SharedPostgis::password);
    }

    @Test
    void providerANeverSeesProviderBsProposals() throws Exception {
        UUID categoryId = insertCategory();
        UUID customerId = insertUser("kc-mine-prop-cust-" + UUID.randomUUID());
        UUID requestId = insertPublishedRequest(customerId, categoryId);

        String providerASub = "kc-mine-prop-prov-a-" + UUID.randomUUID();
        insertEligibleProvider(providerASub, categoryId);
        String providerBSub = "kc-mine-prop-prov-b-" + UUID.randomUUID();
        insertEligibleProvider(providerBSub, categoryId);

        UUID proposalAId = createProposal(providerASub, requestId, 15_000);
        UUID proposalBId = createProposal(providerBSub, requestId, 18_000);

        mockMvc.perform(get("/v1/proposals/me").with(providerJwt(providerASub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + proposalAId + "')]").exists())
                .andExpect(jsonPath("$.items[?(@.id=='" + proposalBId + "')]").doesNotExist());

        mockMvc.perform(get("/v1/proposals/me").with(providerJwt(providerBSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + proposalBId + "')]").exists())
                .andExpect(jsonPath("$.items[?(@.id=='" + proposalAId + "')]").doesNotExist());
    }

    @Test
    void providerWithoutAnyProviderProfileGetsAnEmptyPageNotForbidden() throws Exception {
        String providerSub = "kc-mine-prop-prov-empty-" + UUID.randomUUID();

        mockMvc.perform(get("/v1/proposals/me").with(providerJwt(providerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    private UUID createProposal(String providerSub, UUID requestId, long amountCents) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "price", java.util.Map.of("amountCents", amountCents, "currency", "EUR"),
                "description", "Reparo completo, material incluído."));

        String response = mockMvc.perform(post("/v1/requests/{id}/proposals", requestId)
                        .with(providerJwt(providerSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }

    private static RequestPostProcessor providerJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"));
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

    private UUID insertPublishedRequest(UUID customerId, UUID categoryId) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO service_request (
                            id, customer_id, category_id, title, address_line1, address_postal_code,
                            address_city, address_country, status, published_at
                        ) VALUES (
                            :id, :customerId, :categoryId, 'Fuga de água na cozinha', 'Rua Teste', '1000-001',
                            'Lisboa', 'PT', 'PUBLISHED', now()
                        )
                        """)
                .param("id", id)
                .param("customerId", customerId)
                .param("categoryId", categoryId)
                .update();
        return id;
    }

    private UUID insertEligibleProvider(String keycloakSub, UUID categoryId) {
        UUID userId = insertUser(keycloakSub);
        UUID providerId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO provider_profile (id, user_id, approval_status, visibility_state)
                        VALUES (:id, :userId, 'APPROVED', 'VISIBLE')
                        """)
                .param("id", providerId)
                .param("userId", userId)
                .update();
        jdbcClient.sql("INSERT INTO provider_category (provider_id, category_id) VALUES (:p, :c)")
                .param("p", providerId)
                .param("c", categoryId)
                .update();
        UUID planId = TestDatabase.createPlan(jdbcClient, 0);
        jdbcClient.sql("""
                        INSERT INTO subscription (provider_id, plan_id, status, gateway, current_period_start, current_period_end)
                        VALUES (:p, :plan, 'ACTIVE', 'stripe', now(), now() + interval '30 days')
                        """)
                .param("p", providerId)
                .param("plan", planId)
                .update();
        return providerId;
    }
}
