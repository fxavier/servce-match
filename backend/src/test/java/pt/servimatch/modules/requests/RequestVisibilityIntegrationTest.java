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
                .andExpect(jsonPath("$.items[?(@.id=='" + requestId + "')]").exists())
                // Auditoria confirmada: o inbox do prestador nunca devolve line1
                // nem o código postal completo — só granularidade de zona. Único
                // item elegível nesta fixture, por isso items[0] é seguro.
                .andExpect(jsonPath("$.items[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$.items[0].address.line1").doesNotExist())
                .andExpect(jsonPath("$.items[0].address.postalCode").value("1000"));

        mockMvc.perform(get("/v1/requests/{id}", requestId).with(providerJwt(providerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.address.line1").doesNotExist())
                .andExpect(jsonPath("$.address.postalCode").value("1000"));
    }

    /**
     * Defeito C4 (IDOR), reverificado a nível HTTP: antes desta onda
     * {@code ?status=DRAFT} devolvia rascunhos de qualquer cliente a um
     * prestador, incluindo morada completa e código postal — a mesma
     * granularidade de morada que {@link #eligibleProviderSeesTheRequestInInboxAndDetail}
     * prova estar mascarada para pedidos publicados. {@code DRAFT} é um
     * valor de enum válido, por isso é a allowlist do inbox (não o enum
     * inteiro) que tem de o rejeitar.
     */
    @Test
    void inboxRejectsDraftStatusFilterEvenForAnOtherwiseEligibleProvider() throws Exception {
        UUID categoryId = insertCategory();
        UUID customerId = insertUser("kc-req-vis-cust-draft-" + UUID.randomUUID());
        insertPublishedRequest(customerId, categoryId, 38.7169, -9.1399, "PT-11-LSB");

        String providerSub = "kc-req-vis-prov-draft-" + UUID.randomUUID();
        UUID providerId = insertEligibleProvider(providerSub, categoryId);
        insertRadiusArea(providerId, 38.7169, -9.1399, 5_000);

        mockMvc.perform(get("/v1/providers/me/requests").param("status", "DRAFT").with(providerJwt(providerSub)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/validation"));
    }

    @Test
    void inboxRejectsStatusFilterOutsideTheEnum() throws Exception {
        String providerSub = "kc-req-vis-prov-lixo-" + UUID.randomUUID();
        UUID categoryId = insertCategory();
        UUID providerId = insertEligibleProvider(providerSub, categoryId);
        insertRadiusArea(providerId, 38.7169, -9.1399, 5_000);

        mockMvc.perform(get("/v1/providers/me/requests").param("status", "lixo").with(providerJwt(providerSub)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/validation"));
    }

    @Test
    void inboxAcceptsPublishedStatusFilterAndKeepsZoneAddressExposure() throws Exception {
        UUID categoryId = insertCategory();
        UUID customerId = insertUser("kc-req-vis-cust-pub-" + UUID.randomUUID());
        UUID requestId = insertPublishedRequest(customerId, categoryId, 38.7169, -9.1399, "PT-11-LSB");

        String providerSub = "kc-req-vis-prov-pub-" + UUID.randomUUID();
        UUID providerId = insertEligibleProvider(providerSub, categoryId);
        insertRadiusArea(providerId, 38.7169, -9.1399, 5_000);

        mockMvc.perform(get("/v1/providers/me/requests").param("status", "PUBLISHED").with(providerJwt(providerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + requestId + "')]").exists())
                .andExpect(jsonPath("$.items[0].address.line1").doesNotExist());
    }

    @Test
    void ownerAlwaysSeesTheExactAddressOfTheirOwnRequest() throws Exception {
        UUID categoryId = insertCategory();
        String customerSub = "kc-req-vis-cust-exact-" + UUID.randomUUID();
        UUID customerId = insertUser(customerSub);
        UUID requestId = insertPublishedRequest(customerId, categoryId, 38.7169, -9.1399, "PT-11-LSB");

        mockMvc.perform(get("/v1/requests/{id}", requestId).with(customerJwt(customerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.line1").value("Rua Teste"))
                .andExpect(jsonPath("$.address.postalCode").value("1000-001"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor customerJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
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

    /**
     * Prestador aprovado, visível, com subscrição ativa e a categoria
     * indicada — falta só cobertura geográfica.
     *
     * <p>{@code approval_status='APPROVED'} por {@code INSERT} direto é um
     * atalho de setup para estes testes de elegibilidade/inbox, não uma
     * afirmação de como a aprovação acontece em produção — essa transição
     * ({@code PENDING → APPROVED} pelo endpoint {@code PATCH
     * /v1/admin/providers/{id}/approval}) é responsabilidade de
     * {@code modules/providers} (defeito C1, docs/ESTADO-DO-SISTEMA.md) e
     * tem o seu próprio teste de transição em
     * {@code pt.servimatch.modules.providers.ProviderApprovalIntegrationTest}
     * — e, atravessando pesquisa/inbox, em
     * {@code pt.servimatch.gating.ProviderApprovalUnlocksSearchAndMatchingIntegrationTest}.
     * {@code approval_decided_by}/{@code approval_decided_at} são preenchidos aqui
     * só para satisfazer o {@code CHECK
     * chk_provider_profile_approval_decision_coherence} (V22) — reutiliza-se
     * o próprio {@code userId} do prestador como autor fictício da decisão,
     * porque o único requisito do {@code CHECK} é autoria+instante, não
     * quem especificamente decidiu.
     */
    private UUID insertEligibleProvider(String keycloakSub, UUID categoryId) {
        UUID userId = insertUser(keycloakSub);
        UUID providerId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO provider_profile (
                            id, user_id, approval_status, approval_decided_by, approval_decided_at, visibility_state
                        )
                        VALUES (:id, :userId, 'APPROVED', :userId, now(), 'VISIBLE')
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
