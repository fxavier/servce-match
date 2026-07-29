package pt.servimatch.gating;

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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O <em>gating</em> por subscrição (CLAUDE.md §4: "regra de domínio no
 * servidor", nunca decidida pelo cliente) é a regra central do produto e
 * está espalhada por {@code providers}, {@code billing}, {@code proposals},
 * {@code chat} e {@code search} (ARQUITETURA §3.3/§10.3) — cada módulo já
 * tem teste próprio da sua parte; o que falta, e este teste cobre, é a
 * prova <b>atravessada</b>: o mesmo prestador, sem subscrição ativa,
 * bloqueado em todos os pontos simultaneamente, e destravado quando a
 * subscrição fica {@code ACTIVE}.
 *
 * <p>O listener {@code billing → providers} que escreve
 * {@code provider_profile.visibility_state} a partir de
 * {@code SubscriptionActivated}/{@code SubscriptionExpired} é do
 * {@code backend-payments} e já tem cobertura própria (não repetida aqui,
 * ver {@code SubscriptionLifecycleStateMachineTest}); este teste manipula
 * {@code visibility_state}/{@code subscription} diretamente por SQL para
 * representar o <b>resultado</b> desse listener em cada lado do
 * {@code gating}, tal como {@code RequestVisibilityIntegrationTest} e
 * {@code AcceptProposalIntegrationTest} já fazem — o que se prova aqui é
 * que {@code proposals}/{@code chat}/{@code search}/{@code providers} leem
 * esse resultado corretamente, não a máquina de estados que o produz.
 *
 * <p>Tokens sintéticos (skill {@code testcontainers-integration-test}:
 * "mock para a combinatória") — a cadeia de autenticação real já está
 * provada em {@code pt.servimatch.e2e.FullCustomerJourneyRealAuthIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class SubscriptionGatingAcrossModulesIntegrationTest {

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
    void providerWithoutActiveSubscriptionIsBlockedAtEveryTouchpointAndUnlockedOnceActive() throws Exception {
        UUID categoryId = insertCategory();
        UUID providerId = insertProvider(categoryId, /* subscribed */ false);
        UUID customerId = insertUser("kc-gating-cust-" + UUID.randomUUID());
        UUID requestId = insertPublishedRequest(customerId, categoryId);

        // --- Sem subscrição ativa: bloqueado em todos os pontos. ---

        // search: público, não aparece.
        JsonNode searchWithoutSubscription = readJson(mockMvc.perform(
                        get("/v1/search/providers").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk()));
        assertThat(searchWithoutSubscription.get("items"))
                .as("search não deve listar prestador sem subscrição ativa")
                .noneMatch(item -> item.get("id").asText().equals(providerId.toString()));

        // inbox do prestador: 403.
        mockMvc.perform(get("/v1/providers/me/requests").with(providerJwt(providerId)))
                .andExpect(status().isForbidden());

        // enviar proposta: 403 subscription-required (o contrato documenta este type
        // explicitamente só para este endpoint e para sendMessage — ver
        // pt.servimatch.contract.OpenApiContractComplianceTest).
        mockMvc.perform(post("/v1/requests/{id}/proposals", requestId)
                        .with(providerJwt(providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "price", Map.of("amountCents", 5_000, "currency", "EUR"),
                                "description", "Proposta sem subscrição ativa."))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/subscription-required"));

        // --- Ativa a subscrição (resultado do listener billing -> providers). ---
        activateSubscription(providerId);

        JsonNode searchWithSubscription = readJson(mockMvc.perform(
                        get("/v1/search/providers").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk()));
        assertThat(searchWithSubscription.get("items"))
                .as("search deve listar o prestador assim que a subscrição fica ACTIVE")
                .anyMatch(item -> item.get("id").asText().equals(providerId.toString()));

        mockMvc.perform(get("/v1/providers/me/requests").with(providerJwt(providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + requestId + "')]").exists());

        mockMvc.perform(post("/v1/requests/{id}/proposals", requestId)
                        .with(providerJwt(providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "price", Map.of("amountCents", 5_000, "currency", "EUR"),
                                "description", "Proposta com subscrição ativa."))))
                .andExpect(status().isCreated());
    }

    /**
     * ARQUITETURA §3.3: uma conversa já criada não desaparece quando a
     * subscrição do prestador expira — fica <b>legível</b> para ele (só
     * deixa de poder escrever), e o cliente continua sempre a poder
     * escrever.
     */
    @Test
    void existingConversationStaysReadableNotInvisibleWhenProviderSubscriptionExpires() throws Exception {
        UUID categoryId = insertCategory();
        UUID providerId = insertProvider(categoryId, /* subscribed */ true);
        UUID customerId = insertUser("kc-gating-conv-cust-" + UUID.randomUUID());
        UUID requestId = insertPublishedRequest(customerId, categoryId);

        UUID proposalId = UUID.fromString(readJson(mockMvc.perform(post("/v1/requests/{id}/proposals", requestId)
                        .with(providerJwt(providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "price", Map.of("amountCents", 8_000, "currency", "EUR"),
                                "description", "Proposta inicial, subscrição ativa.")))))
                .get("id").asText());

        mockMvc.perform(post("/v1/proposals/{id}/accept", proposalId).with(customerJwt(customerId)))
                .andExpect(status().isOk());

        UUID conversationId = awaitConversation(requestId, providerId);

        // Cliente escreve enquanto o prestador ainda está subscrito.
        mockMvc.perform(post("/v1/conversations/{id}/messages", conversationId)
                        .with(customerJwt(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Olá, tudo bem para amanhã?"))))
                .andExpect(status().isCreated());

        // Subscrição expira (resultado do listener billing -> providers).
        expireSubscription(providerId);

        // A conversa continua LEGÍVEL para o prestador — nunca invisível/404/403.
        mockMvc.perform(get("/v1/conversations/{id}/messages", conversationId).with(providerJwt(providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)));

        // Mas o prestador já não pode escrever.
        mockMvc.perform(post("/v1/conversations/{id}/messages", conversationId)
                        .with(providerJwt(providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Ainda posso responder?"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/subscription-required"));

        // O cliente continua sempre a poder escrever.
        mockMvc.perform(post("/v1/conversations/{id}/messages", conversationId)
                        .with(customerJwt(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Sem problema, ficamos por mensagem."))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/conversations/{id}/messages", conversationId).with(customerJwt(customerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(2)));
    }

    /**
     * Achado (documentado, não corrigido — {@code requests} é do
     * {@code backend-domain}): {@code listProviderInbox} devolve 403 com
     * {@code type=https://errors.servimatch.pt/forbidden} para exatamente a
     * mesma condição ("subscrição inativa ou perfil não aprovado") que
     * {@code createProposal} e {@code sendMessage} devolvem como
     * {@code type=.../subscription-required}. O contrato não obriga
     * literalmente o segundo type para este endpoint (a descrição do 403 em
     * {@code listProviderInbox} não o nomeia, ao contrário de
     * {@code createProposal}), mas a inconsistência entre três endpoints
     * que implementam a mesma regra de negócio significa que um cliente não
     * consegue distinguir programaticamente "sem subscrição" de "outro erro
     * de autorização" só neste endpoint. Desativado para não bloquear
     * {@code mvn verify}; reativar quando {@code requests} adotar
     * {@code subscription-required} aqui também.
     */
    @org.junit.jupiter.api.Disabled("Inconsistência reportada ao backend-domain (ver javadoc): listProviderInbox usa "
            + "type=forbidden, não subscription-required, para o mesmo gating que proposals/chat já tipificam.")
    @Test
    void providerInboxUsesTheSameSubscriptionRequiredTypeAsProposalsAndChat() throws Exception {
        UUID categoryId = insertCategory();
        UUID providerId = insertProvider(categoryId, /* subscribed */ false);

        mockMvc.perform(get("/v1/providers/me/requests").with(providerJwt(providerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/subscription-required"));
    }

    // ---------------------------------------------------------------- Helpers

    private JsonNode readJson(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private RequestPostProcessor customerJwt(UUID customerUserId) {
        String sub = jdbcClient.sql("SELECT keycloak_sub FROM users WHERE id = :id").param("id", customerUserId).query(String.class).single();
        return jwt().jwt(b -> b.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private RequestPostProcessor providerJwt(UUID providerId) {
        String sub = jdbcClient.sql("""
                        SELECT u.keycloak_sub FROM users u JOIN provider_profile p ON p.user_id = u.id WHERE p.id = :id
                        """)
                .param("id", providerId)
                .query(String.class)
                .single();
        return jwt().jwt(b -> b.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"));
    }

    private UUID insertCategory() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO category (id, slug, name) VALUES (:id, :slug, 'Categoria Gating')")
                .param("id", id)
                .param("slug", "categoria-gating-" + id)
                .update();
        return id;
    }

    private UUID insertUser(String keycloakSub) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO users (id, keycloak_sub, email, display_name)
                        VALUES (:id, :sub, :email, 'Utilizador Gating')
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
                            address_city, address_country, location, status, published_at
                        ) VALUES (
                            :id, :customerId, :categoryId, 'Fuga de água na cozinha', 'Rua Teste', '1000-001',
                            'Lisboa', 'PT', ST_SetSRID(ST_MakePoint(-9.1399, 38.7169), 4326)::geography, 'PUBLISHED', now()
                        )
                        """)
                .param("id", id)
                .param("customerId", customerId)
                .param("categoryId", categoryId)
                .update();
        return id;
    }

    /** Aprovado sempre; visível/subscrito só se {@code subscribed}. Categoria e cobertura de raio já atribuídas. */
    private UUID insertProvider(UUID categoryId, boolean subscribed) {
        UUID userId = insertUser("kc-gating-prov-" + UUID.randomUUID());
        UUID providerId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO provider_profile (id, user_id, approval_status, visibility_state)
                        VALUES (:id, :userId, 'APPROVED', :visibility)
                        """)
                .param("id", providerId)
                .param("userId", userId)
                .param("visibility", subscribed ? "VISIBLE" : "HIDDEN")
                .update();
        jdbcClient.sql("INSERT INTO provider_category (provider_id, category_id) VALUES (:p, :c)")
                .param("p", providerId)
                .param("c", categoryId)
                .update();
        jdbcClient.sql("""
                        INSERT INTO provider_service_area (provider_id, mode, center, radius_m)
                        VALUES (:p, 'RADIUS', ST_SetSRID(ST_MakePoint(-9.1399, 38.7169), 4326)::geography, 5000)
                        """)
                .param("p", providerId)
                .update();
        if (subscribed) {
            activateSubscription(providerId);
        }
        return providerId;
    }

    private void activateSubscription(UUID providerId) {
        jdbcClient.sql("UPDATE provider_profile SET visibility_state = 'VISIBLE' WHERE id = :p")
                .param("p", providerId)
                .update();
        boolean hasSubscription = jdbcClient.sql("SELECT count(*) FROM subscription WHERE provider_id = :p")
                .param("p", providerId).query(Integer.class).single() > 0;
        if (hasSubscription) {
            jdbcClient.sql("UPDATE subscription SET status = 'ACTIVE', current_period_start = now(), "
                            + "current_period_end = now() + interval '30 days' WHERE provider_id = :p")
                    .param("p", providerId)
                    .update();
        } else {
            UUID planId = TestDatabase.createPlan(jdbcClient, 0);
            jdbcClient.sql("""
                            INSERT INTO subscription (provider_id, plan_id, status, gateway, current_period_start, current_period_end)
                            VALUES (:p, :plan, 'ACTIVE', 'stripe', now(), now() + interval '30 days')
                            """)
                    .param("p", providerId)
                    .param("plan", planId)
                    .update();
        }
    }

    private void expireSubscription(UUID providerId) {
        jdbcClient.sql("UPDATE provider_profile SET visibility_state = 'HIDDEN' WHERE id = :p")
                .param("p", providerId)
                .update();
        jdbcClient.sql("UPDATE subscription SET status = 'EXPIRED' WHERE provider_id = :p")
                .param("p", providerId)
                .update();
    }

    private UUID awaitConversation(UUID requestId, UUID providerId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (true) {
            var row = jdbcClient.sql("SELECT id FROM conversation WHERE request_id = :r AND provider_id = :p")
                    .param("r", requestId)
                    .param("p", providerId)
                    .query(UUID.class)
                    .optional();
            if (row.isPresent() || System.currentTimeMillis() > deadline) {
                return row.orElseThrow();
            }
            Thread.sleep(100);
        }
    }
}
