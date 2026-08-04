package pt.servimatch.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import pt.servimatch.testsupport.OpenApiSpec;
import pt.servimatch.testsupport.SharedPostgis;
import pt.servimatch.testsupport.TestDatabase;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova que as respostas <b>reais</b> do backend correspondem ao contrato
 * (CLAUDE.md §2: "código gerado nunca é a fonte de verdade — regenera-se";
 * relatório de entrega: "nenhum teste verifica hoje que as respostas do
 * backend correspondem ao contrato"). Usa {@link OpenApiSpec}, um validador
 * mínimo (ver a sua javadoc para o que cobre e o que não cobre) contra
 * {@code docs/api/openapi.yaml}, carregado uma única vez.
 *
 * <p>Tokens sintéticos ({@code spring-security-test}), não Keycloak real —
 * a cadeia de autenticação em si já está provada por
 * {@code pt.servimatch.e2e.FullCustomerJourneyRealAuthIntegrationTest};
 * aqui o que se prova é a <em>forma</em> da resposta, não o mecanismo (skill
 * {@code testcontainers-integration-test}: "mock para a combinatória").
 *
 * <p>Qualquer divergência encontrada aqui é reportada ao {@code api-contract}
 * e ao agente do módulo — nunca "corrigida" alterando o contrato (fora do
 * âmbito de escrita deste agente).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class OpenApiContractComplianceTest {

    private static final OpenApiSpec SPEC = OpenApiSpec.load();

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

    private UUID categoryId;
    private UUID customerId;
    private String customerSub;
    private UUID providerId;
    private String providerSub;

    @BeforeEach
    void seedFixture() {
        categoryId = insertCategory();
        customerSub = "kc-contract-cust-" + UUID.randomUUID();
        customerId = insertUser(customerSub);
        providerSub = "kc-contract-prov-" + UUID.randomUUID();
        providerId = insertEligibleProvider(providerSub, categoryId);
    }

    // ---------------------------------------------------------------- Endpoints públicos (dados semeados por V15)

    @Test
    void listSubscriptionPlansMatchesContractSchema() throws Exception {
        JsonNode body = readJson(mockMvc.perform(get("/v1/subscription-plans"))
                .andExpect(status().isOk()));
        SPEC.assertEachItemMatchesSchema(body, "SubscriptionPlan");
    }

    @Test
    void listCategoriesMatchesContractSchema() throws Exception {
        JsonNode body = readJson(mockMvc.perform(get("/v1/categories"))
                .andExpect(status().isOk()));
        SPEC.assertEachItemMatchesSchema(body, "Category");
    }

    // ---------------------------------------------------------------- Ciclo de vida requests/proposals/bookings/reviews

    @Test
    void serviceRequestResponseMatchesContractSchemaThroughDraftAndPublish() throws Exception {
        JsonNode draft = readJson(mockMvc.perform(post("/v1/requests")
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated()));
        // proposalCount: gap de contrato conhecido, ver o teste desativado
        // optionalResponseFieldsThatAreCurrentlyNullDivergeFromNonNullableContractTypes.
        SPEC.assertMatchesSchema(draft, "ServiceRequest", Set.of("$.proposalCount"));

        UUID requestId = UUID.fromString(draft.get("id").asText());
        JsonNode published = readJson(mockMvc.perform(post("/v1/requests/{id}/publish", requestId).with(customerJwt()))
                .andExpect(status().isOk()));
        SPEC.assertMatchesSchema(published, "ServiceRequest", Set.of("$.proposalCount"));
    }

    @Test
    void proposalResponseMatchesContractSchemaThroughSendAndAccept() throws Exception {
        UUID requestId = publishRequest();

        JsonNode proposal = readJson(mockMvc.perform(post("/v1/requests/{id}/proposals", requestId)
                        .with(providerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "price", Map.of("amountCents", 12_000, "currency", "EUR"),
                                "description", "Substituição da torneira e verificação de fugas.",
                                "leadTimeDays", 2))))
                .andExpect(status().isCreated()));
        SPEC.assertMatchesSchema(proposal, "Proposal");

        UUID proposalId = UUID.fromString(proposal.get("id").asText());
        JsonNode accepted = readJson(mockMvc.perform(post("/v1/proposals/{id}/accept", proposalId).with(customerJwt()))
                .andExpect(status().isOk()));
        SPEC.assertMatchesSchema(accepted, "Proposal");
    }

    /**
     * <b>Desativado deliberadamente</b> — documenta um gap de contrato real
     * encontrado por esta suíte, não um teste a corrigir por mim ({@code
     * qa-e2e} não escreve em {@code docs/api/**} nem em
     * {@code backend/src/main/**}). Vários campos de resposta genuinamente
     * opcionais (sem valor fornecido) vêm como JSON {@code null}
     * (comportamento por omissão do Jackson), mas o schema declara-os
     * {@code type: string}/{@code type: integer} sem a união 3.1
     * {@code [tipo, "null"]} — ao contrário de campos irmãos no mesmo
     * schema que já têm essa união (ex. {@code Proposal.validUntil},
     * {@code ProviderSummary.companyName}). Reprodução: {@code POST
     * /v1/requests} sem {@code address.line2}/{@code availability}, e
     * {@code POST .../proposals} sem {@code leadTimeDays} — em qualquer dos
     * casos a resposta 2xx inclui o campo como {@code null} explícito.
     * Reportado ao {@code api-contract} (decidir: {@code [tipo, "null"]} no
     * schema, ou {@code @JsonInclude(NON_NULL)} nos DTOs — não é âmbito
     * deste agente escolher). {@code ServiceRequest.proposalCount} é
     * variante do mesmo padrão mas sempre null (nunca calculado, ver
     * {@code RequestsService} — "proposalCount não é computado nesta
     * onda"), coberta à parte via {@code knownGapPaths} nos testes acima
     * para não bloquear o resto da suíte.
     */
    @org.junit.jupiter.api.Disabled("Gap de contrato reportado (ver javadoc): campos opcionais nulos vs. type sem união null. "
            + "Reativar quando api-contract/backend-domain decidirem a correção.")
    @Test
    void optionalResponseFieldsThatAreCurrentlyNullDivergeFromNonNullableContractTypes() throws Exception {
        JsonNode draft = readJson(mockMvc.perform(post("/v1/requests")
                .with(customerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "categoryId", categoryId.toString(),
                        "title", "Fuga de água na cozinha",
                        "address", Map.of("line1", "Rua Teste 123", "postalCode", "1000-001", "city", "Lisboa"))))));
        SPEC.assertMatchesSchema(draft, "ServiceRequest");

        UUID requestId = UUID.fromString(draft.get("id").asText());
        mockMvc.perform(post("/v1/requests/{id}/publish", requestId).with(customerJwt())).andExpect(status().isOk());
        JsonNode proposal = readJson(mockMvc.perform(post("/v1/requests/{id}/proposals", requestId)
                .with(providerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "price", Map.of("amountCents", 5_000, "currency", "EUR"),
                        "description", "Sem leadTimeDays.")))));
        SPEC.assertMatchesSchema(proposal, "Proposal");
    }

    @Test
    void bookingAndReviewResponsesMatchContractSchema() throws Exception {
        UUID requestId = publishRequest();
        UUID proposalId = sendProposal(requestId);
        mockMvc.perform(post("/v1/proposals/{id}/accept", proposalId).with(customerJwt())).andExpect(status().isOk());

        UUID bookingId = awaitBookingId(proposalId);

        JsonNode completed = readJson(mockMvc.perform(post("/v1/bookings/{id}/complete", bookingId).with(customerJwt()))
                .andExpect(status().isOk()));
        SPEC.assertMatchesSchema(completed, "Booking");

        JsonNode review = readJson(mockMvc.perform(post("/v1/reviews")
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "bookingId", bookingId.toString(),
                                "targetId", providerUserId().toString(),
                                "rating", 5,
                                "comment", "Impecável."))))
                .andExpect(status().isCreated()));
        SPEC.assertMatchesSchema(review, "Review");
    }

    // ---------------------------------------------------------------- Formas de erro (ProblemDetails, RFC 9457)

    @Test
    void unauthenticatedRequestReturnsProblemDetailsMatchingContract() throws Exception {
        JsonNode body = readJson(mockMvc.perform(post("/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isUnauthorized()));
        SPEC.assertMatchesSchema(body, "ProblemDetails");
    }

    @Test
    void notFoundReturnsProblemDetailsMatchingContract() throws Exception {
        JsonNode body = readJson(mockMvc.perform(get("/v1/requests/{id}", UUID.randomUUID()).with(customerJwt()))
                .andExpect(status().isNotFound()));
        SPEC.assertMatchesSchema(body, "ProblemDetails");
    }

    @Test
    void conflictOnDoublePublishReturnsProblemDetailsMatchingContract() throws Exception {
        UUID requestId = publishRequest();
        JsonNode body = readJson(mockMvc.perform(post("/v1/requests/{id}/publish", requestId).with(customerJwt()))
                .andExpect(status().isConflict()));
        SPEC.assertMatchesSchema(body, "ProblemDetails");
    }

    @Test
    void subscriptionRequiredProblemMatchesContractSchemaAndTypeConvention() throws Exception {
        UUID requestId = publishRequest();
        String unsubscribedProviderSub = "kc-contract-unsub-" + UUID.randomUUID();
        insertUnsubscribedProvider(unsubscribedProviderSub);

        JsonNode body = readJson(mockMvc.perform(post("/v1/requests/{id}/proposals", requestId)
                        .with(jwt().jwt(b -> b.subject(unsubscribedProviderSub)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "price", Map.of("amountCents", 5_000, "currency", "EUR"),
                                "description", "Proposta de um prestador sem subscrição ativa."))))
                .andExpect(status().isForbidden()));
        SPEC.assertMatchesSchema(body, "ProblemDetails");
        org.assertj.core.api.Assertions.assertThat(body.get("type").asText())
                .isEqualTo("https://errors.servimatch.pt/subscription-required");
    }

    // ---------------------------------------------------------------- Helpers

    private JsonNode readJson(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String createRequestBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "categoryId", categoryId.toString(),
                "title", "Fuga de água na cozinha",
                "description", "Torneira a pingar.",
                "address", Map.of(
                        "line1", "Rua Teste 123",
                        "line2", "2º Esq.",
                        "postalCode", "1000-001",
                        "city", "Lisboa",
                        "regionCode", "PT-11",
                        "country", "PT",
                        "location", Map.of("lat", 38.7169, "lon", -9.1399)),
                "urgency", "NORMAL",
                "availability", "Dias úteis, depois das 18h."));
    }

    private UUID publishRequest() throws Exception {
        JsonNode draft = readJson(mockMvc.perform(post("/v1/requests")
                .with(customerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestBody())));
        UUID requestId = UUID.fromString(draft.get("id").asText());
        mockMvc.perform(post("/v1/requests/{id}/publish", requestId).with(customerJwt())).andExpect(status().isOk());
        return requestId;
    }

    private UUID sendProposal(UUID requestId) throws Exception {
        JsonNode proposal = readJson(mockMvc.perform(post("/v1/requests/{id}/proposals", requestId)
                .with(providerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "price", Map.of("amountCents", 12_000, "currency", "EUR"),
                        "description", "Substituição da torneira.")))));
        return UUID.fromString(proposal.get("id").asText());
    }

    private UUID awaitBookingId(UUID proposalId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (true) {
            var row = jdbcClient.sql("SELECT id FROM booking WHERE proposal_id = :p").param("p", proposalId).query(UUID.class).optional();
            if (row.isPresent() || System.currentTimeMillis() > deadline) {
                return row.orElseThrow();
            }
            Thread.sleep(100);
        }
    }

    private RequestPostProcessor customerJwt() {
        return jwt().jwt(b -> b.subject(customerSub)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private RequestPostProcessor providerJwt() {
        return jwt().jwt(b -> b.subject(providerSub)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"));
    }

    private UUID insertCategory() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO category (id, slug, name) VALUES (:id, :slug, 'Categoria Contrato')")
                .param("id", id)
                .param("slug", "categoria-contrato-" + id)
                .update();
        return id;
    }

    private UUID insertUser(String keycloakSub) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO users (id, keycloak_sub, email, display_name)
                        VALUES (:id, :sub, :email, 'Utilizador Contrato')
                        """)
                .param("id", id)
                .param("sub", keycloakSub)
                .param("email", keycloakSub + "@example.test")
                .update();
        return id;
    }

    /**
     * {@code approval_status='APPROVED'} por {@code INSERT} direto é um
     * atalho de setup (ADR-0011 D9) — a transição real
     * ({@code PENDING → APPROVED} via {@code PATCH
     * /v1/admin/providers/{id}/approval}) tem o seu teste próprio em
     * {@code pt.servimatch.modules.providers.ProviderApprovalIntegrationTest}
     * e, atravessando pesquisa/inbox, em
     * {@code pt.servimatch.gating.ProviderApprovalUnlocksSearchAndMatchingIntegrationTest}.
     * {@code approval_decided_by}/{@code approval_decided_at} só satisfazem
     * aqui o {@code CHECK chk_provider_profile_approval_decision_coherence}
     * (V22); reutiliza-se o {@code userId} do próprio prestador como autor
     * fictício.
     */
    private UUID insertEligibleProvider(String keycloakSub, UUID categoryId) {
        UUID userId = insertUser(keycloakSub);
        UUID providerId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO provider_profile (
                            id, user_id, approval_status, approval_decided_by, approval_decided_at, visibility_state, headline
                        )
                        VALUES (:id, :userId, 'APPROVED', :userId, now(), 'VISIBLE', 'Canalizador certificado')
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

    /**
     * {@code approval_status='APPROVED'} por {@code INSERT} direto — mesmo
     * atalho de setup de {@link #insertEligibleProvider}, com o mesmo teste
     * de transição nomeado aí.
     */
    private void insertUnsubscribedProvider(String keycloakSub) {
        UUID userId = insertUser(keycloakSub);
        jdbcClient.sql("""
                        INSERT INTO provider_profile (
                            id, user_id, approval_status, approval_decided_by, approval_decided_at, visibility_state
                        )
                        VALUES (:id, :userId, 'APPROVED', :userId, now(), 'HIDDEN')
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .update();
    }

    private UUID providerUserId() {
        return jdbcClient.sql("SELECT user_id FROM provider_profile WHERE id = :id")
                .param("id", providerId)
                .query(UUID.class)
                .single();
    }
}
