package pt.servimatch.gating;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pt.servimatch.testsupport.SharedPostgis;
import pt.servimatch.testsupport.TestDatabase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fecha o defeito C1 ({@code docs/ESTADO-DO-SISTEMA.md}) de facto — não só a
 * escrita isolada de {@code approval_status}
 * ({@code pt.servimatch.modules.providers.ProviderApprovalIntegrationTest},
 * que prova a transição em si), mas o que essa transição estava a impedir em
 * produção: um prestador criado pelo caminho normal nunca aparecia em
 * <b>nenhum</b> ponto de entrada de negócio, porque nada escrevia
 * {@code approval_status}.
 *
 * <p>Sequência (skill {@code estado-com-escritor}, "assevera sempre os dois
 * lados"): prestador provisionado pelo caminho de produção
 * ({@code GET /v1/providers/me}, {@code PUT /v1/providers/me}) fica
 * {@code PENDING} — confirma-se que <b>não</b> aparece em
 * {@code GET /v1/search/providers} nem em {@code GET
 * /v1/providers/me/requests} (o "inbox de matching"), apesar de ter
 * categoria, cobertura geográfica e subscrição ativa; só depois disso o
 * {@code PATCH /v1/admin/providers/{id}/approval} por um ADMIN muda o
 * estado, e só então os dois pontos de entrada passam a mostrá-lo. Nenhum
 * {@code UPDATE approval_status} por SQL em nenhum momento — é exatamente a
 * escrita que este teste existe para exercitar; ver
 * {@code docs/prompts-onda-c1/c1-qa-e2e.txt}, tarefa 1: "só o lado positivo
 * deixaria um regresso do defeito passar despercebido".
 *
 * <p>A subscrição {@code ACTIVE} é fabricada por {@code INSERT} direto
 * (helper {@link #activateSubscriptionByShortcut(UUID)}) — a transição do
 * seu próprio ciclo de vida não é o que este teste prova; já tem cobertura
 * de produção própria em {@code
 * pt.servimatch.modules.billing.SubscriptionLifecycleStateMachineTest} e em
 * {@code pt.servimatch.modules.payments.PaymentsApiIntegrationTest}, e o
 * caminho "subscrição ACTIVE genuína, sem tocar em visibility_state"
 * está coberto por
 * {@link ProviderVisibilityWithoutBillingListenerIntegrationTest}.
 *
 * <p>Tokens sintéticos (skill {@code testcontainers-integration-test}:
 * "mock para a combinatória") — a cadeia de autenticação real já está
 * provada em {@code pt.servimatch.e2e.FullCustomerJourneyRealAuthIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class ProviderApprovalUnlocksSearchAndMatchingIntegrationTest {

    private static final String REGION_CODE = "PT-LIS";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgis::jdbcUrl);
        registry.add("spring.datasource.username", SharedPostgis::username);
        registry.add("spring.datasource.password", SharedPostgis::password);
    }

    @Test
    void providerPendingApprovalIsInvisibleEverywhereThenAppearsInSearchAndInboxOnceAdminApproves() throws Exception {
        UUID categoryId = insertCategory();

        // 1. Prestador provisionado pelo caminho de produção — a primeira GET
        // aciona ProvidersApi#ensureProvisioned, nunca um INSERT do teste.
        // Fica PENDING, tal como qualquer prestador novo em produção.
        String providerSub = "kc-approval-search-prov-" + UUID.randomUUID();
        UUID providerId = provisionProviderByProductionPath(providerSub);
        assertThat(currentApprovalStatus(providerId)).isEqualTo("PENDING");

        // 2. Categoria e cobertura geográfica pelo caminho de produção
        // (PUT /v1/providers/me), nunca por INSERT em provider_category /
        // provider_service_area.
        putProviderProfile(providerSub, categoryId);

        // 3. Subscrição ACTIVE — atalho tolerado (ver javadoc da classe),
        // não o que este teste prova.
        activateSubscriptionByShortcut(providerId);

        // 4. Pedido publicado na mesma categoria/região, pelo caminho de
        // produção (cliente).
        String customerSub = "kc-approval-search-cust-" + UUID.randomUUID();
        UUID requestId = createAndPublishRequest(customerSub, categoryId);

        // --- Lado 1: PENDING, apesar de categoria+região+subscrição ativas, não aparece em lado nenhum. ---

        JsonNode searchBeforeApproval = readJson(mockMvc.perform(
                        get("/v1/search/providers").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk()));
        assertThat(searchBeforeApproval.get("items"))
                .as("prestador PENDING não pode aparecer na pesquisa pública, mesmo com categoria/região/subscrição prontas")
                .noneMatch(item -> item.get("id").asText().equals(providerId.toString()));

        mockMvc.perform(get("/v1/providers/me/requests").with(providerJwt(providerSub)))
                .andExpect(status().isForbidden());

        assertThat(currentApprovalStatus(providerId))
                .as("nenhuma das leituras acima pode ter mudado o estado")
                .isEqualTo("PENDING");

        // --- Transição real: PATCH por ADMIN, nunca UPDATE SQL. ---

        String adminSub = "kc-approval-search-admin-" + UUID.randomUUID();
        mockMvc.perform(patch("/v1/admin/providers/{id}/approval", providerId)
                        .with(adminJwt(adminSub))
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
        assertThat(currentApprovalStatus(providerId)).isEqualTo("APPROVED");

        // --- Lado 2: agora aparece nos dois pontos de entrada. ---

        JsonNode searchAfterApproval = readJson(mockMvc.perform(
                        get("/v1/search/providers").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk()));
        assertThat(searchAfterApproval.get("items"))
                .as("prestador APPROVED com categoria/região/subscrição ativas deve aparecer na pesquisa pública")
                .anyMatch(item -> item.get("id").asText().equals(providerId.toString()));

        JsonNode inboxAfterApproval = readJson(mockMvc.perform(get("/v1/providers/me/requests").with(providerJwt(providerSub)))
                .andExpect(status().isOk()));
        assertThat(inboxAfterApproval.get("items"))
                .as("o pedido publicado na mesma categoria/região deve aparecer no inbox de matching do prestador aprovado")
                .anyMatch(item -> item.get("id").asText().equals(requestId.toString()));
    }

    // ---------------------------------------------------------------- Helpers

    private JsonNode readJson(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Aciona o provisionamento JIT pelo caminho de produção (nunca INSERT) e devolve o {@code provider_profile.id}. */
    private UUID provisionProviderByProductionPath(String providerSub) throws Exception {
        String body = mockMvc.perform(get("/v1/providers/me").with(providerJwt(providerSub)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    /** {@code PUT /v1/providers/me} — caminho de produção para categoria e cobertura ADMIN_REGION. */
    private void putProviderProfile(String providerSub, UUID categoryId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("headline", "Canalizador certificado");
        body.put("bio", "Prestador de teste para o fecho do defeito C1.");
        body.put("categoryIds", List.of(categoryId.toString()));
        body.put("regionCodes", List.of(REGION_CODE));
        body.put("portfolioImageIds", List.of());

        mockMvc.perform(put("/v1/providers/me")
                        .with(providerJwt(providerSub))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    /**
     * Subscrição {@code ACTIVE} fabricada por {@code INSERT} direto — atalho
     * tolerado (ADR-0011 D9): não é a transição que este teste prova (ver
     * javadoc da classe para onde essa transição está coberta).
     */
    private void activateSubscriptionByShortcut(UUID providerId) {
        UUID planId = TestDatabase.createPlan(jdbcClient, 0);
        jdbcClient.sql("""
                        INSERT INTO subscription (provider_id, plan_id, status, gateway, current_period_start, current_period_end)
                        VALUES (:p, :plan, 'ACTIVE', 'stripe', now(), now() + interval '30 days')
                        """)
                .param("p", providerId)
                .param("plan", planId)
                .update();
    }

    /** Cliente pelo caminho de produção: cria e publica um pedido na categoria/região do prestador. */
    private UUID createAndPublishRequest(String customerSub, UUID categoryId) throws Exception {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("line1", "Rua Teste 123");
        address.put("postalCode", "1000-001");
        address.put("city", "Lisboa");
        address.put("regionCode", REGION_CODE);
        address.put("country", "PT");

        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("categoryId", categoryId.toString());
        createBody.put("title", "Fuga de água na cozinha");
        createBody.put("description", "Torneira a pingar sem parar desde ontem.");
        createBody.put("address", address);
        createBody.put("urgency", "NORMAL");

        String created = mockMvc.perform(post("/v1/requests")
                        .with(customerJwt(customerSub))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID requestId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(post("/v1/requests/{id}/publish", requestId).with(customerJwt(customerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        return requestId;
    }

    private String currentApprovalStatus(UUID providerId) {
        return jdbcClient.sql("SELECT approval_status FROM provider_profile WHERE id = :id")
                .param("id", providerId)
                .query(String.class)
                .single();
    }

    private UUID insertCategory() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO category (id, slug, name) VALUES (:id, :slug, 'Categoria Aprovação')")
                .param("id", id)
                .param("slug", "categoria-aprovacao-" + id)
                .update();
        return id;
    }

    private static RequestPostProcessor providerJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"));
    }

    private static RequestPostProcessor customerJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor adminJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
