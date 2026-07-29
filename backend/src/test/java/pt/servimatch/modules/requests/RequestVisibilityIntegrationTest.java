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
import pt.servimatch.testsupport.SharedPostgis;
import pt.servimatch.testsupport.TestDatabase;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova contra PostGIS real ({@link SharedPostgis}) que {@code GET
 * /v1/providers/me/requests} e {@code GET /v1/requests/{id}} só mostram a um
 * prestador pedidos para os quais é <b>elegível</b> (ADR-0004 §10.3:
 * categoria trabalhada + cobertura geográfica) — a fuga de dados corrigida
 * nesta onda (ver relatório de entrega): antes desta correção, qualquer
 * prestador com subscrição ativa via <b>todos</b> os pedidos publicados do
 * país, incluindo morada do cliente, independentemente de categoria ou zona
 * de cobertura.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class RequestVisibilityIntegrationTest {

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
    void providerOutsideTheCoverageZoneDoesNotSeeAnOtherwiseEligibleRequest() throws Exception {
        UUID categoryId = insertCategory();
        UUID customerId = insertUser("kc-req-vis-cust-out-" + UUID.randomUUID());
        // Lisboa.
        UUID requestId = insertPublishedRequest(customerId, categoryId, 38.7169, -9.1399, "PT-11-LSB");

        String providerSub = "kc-req-vis-prov-out-" + UUID.randomUUID();
        UUID providerId = insertEligibleProvider(providerSub, categoryId);
        // Área de cobertura no Porto: mesma categoria, zona bem distinta.
        insertRadiusArea(providerId, 41.1579, -8.6291, 5_000);

        assertProviderDoesNotSeeRequest(providerSub, requestId);
    }

    @Test
    void providerWithoutTheWorkedCategoryDoesNotSeeAnOtherwiseEligibleRequest() throws Exception {
        UUID requestCategoryId = insertCategory();
        UUID otherCategoryId = insertCategory();
        UUID customerId = insertUser("kc-req-vis-cust-cat-" + UUID.randomUUID());
        UUID requestId = insertPublishedRequest(customerId, requestCategoryId, 38.7169, -9.1399, "PT-11-LSB");

        String providerSub = "kc-req-vis-prov-cat-" + UUID.randomUUID();
        // Prestador cobre a mesma zona geográfica, mas trabalha outra categoria.
        UUID providerId = insertEligibleProvider(providerSub, otherCategoryId);
        insertRadiusArea(providerId, 38.7169, -9.1399, 5_000);

        assertProviderDoesNotSeeRequest(providerSub, requestId);
    }

    @Test
    void eligibleProviderSeesTheRequestInInboxAndDetail() throws Exception {
        UUID categoryId = insertCategory();
        UUID customerId = insertUser("kc-req-vis-cust-ok-" + UUID.randomUUID());
        UUID requestId = insertPublishedRequest(customerId, categoryId, 38.7169, -9.1399, "PT-11-LSB");

        String providerSub = "kc-req-vis-prov-ok-" + UUID.randomUUID();
        UUID providerId = insertEligibleProvider(providerSub, categoryId);
        insertRadiusArea(providerId, 38.7169, -9.1399, 5_000);

        mockMvc.perform(get("/v1/providers/me/requests").with(providerJwt(providerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + requestId + "')]").exists());

        mockMvc.perform(get("/v1/requests/{id}", requestId).with(providerJwt(providerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()));
    }

    private void assertProviderDoesNotSeeRequest(String providerSub, UUID requestId) throws Exception {
        mockMvc.perform(get("/v1/providers/me/requests").with(providerJwt(providerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + requestId + "')]").doesNotExist());

        mockMvc.perform(get("/v1/requests/{id}", requestId).with(providerJwt(providerSub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/forbidden"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor providerJwt(String subject) {
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

    private UUID insertPublishedRequest(UUID customerId, UUID categoryId, double lat, double lon, String regionCode) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO service_request (
                            id, customer_id, category_id, title, address_line1, address_postal_code, address_city,
                            address_region_code, location, status, published_at
                        ) VALUES (
                            :id, :customerId, :categoryId, 'Fuga de água na cozinha', 'Rua Teste', '1000-001', 'Lisboa',
                            :regionCode, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, 'PUBLISHED', now()
                        )
                        """)
                .param("id", id)
                .param("customerId", customerId)
                .param("categoryId", categoryId)
                .param("regionCode", regionCode)
                .param("lat", lat)
                .param("lon", lon)
                .update();
        return id;
    }

    /** Prestador aprovado, visível, com subscrição ativa e a categoria indicada — falta só cobertura geográfica. */
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

    private void insertRadiusArea(UUID providerId, double lat, double lon, int radiusM) {
        jdbcClient.sql("""
                        INSERT INTO provider_service_area (provider_id, mode, center, radius_m)
                        VALUES (:p, 'RADIUS', ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius)
                        """)
                .param("p", providerId)
                .param("lat", lat)
                .param("lon", lon)
                .param("radius", radiusM)
                .update();
    }
}
