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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova contra PostGIS real ({@link SharedPostgis}) a transição central de
 * {@code acceptProposal} (CLAUDE.md — "aceitar uma proposta confirma o
 * pedido e invalida as concorrentes numa única transação"): dois
 * prestadores enviam proposta ao mesmo pedido, o cliente aceita uma, e
 * numa só chamada HTTP:
 * <ul>
 *   <li>a proposta aceite fica {@code ACCEPTED}, a concorrente {@code SUPERSEDED};</li>
 *   <li>o pedido fica {@code CONFIRMED};</li>
 *   <li>uma {@code Booking CONFIRMED} é criada para a proposta aceite
 *       ({@code bookings.internal.ProposalAcceptedListener});</li>
 *   <li>uma {@code Conversation} é criada para {@code (requestId, providerId)}
 *       da proposta aceite ({@code chat.internal.ProposalAcceptedListener}).</li>
 * </ul>
 * As duas últimas reagem a {@code ProposalAccepted} via
 * {@code @ApplicationModuleListener} — assíncrono por definição — daí o
 * <em>polling</em> curto em {@link #awaitRow}, em vez de as esperar prontas
 * logo após a resposta HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class AcceptProposalIntegrationTest {

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
    void acceptingAProposalConfirmsTheRequestSupersedesTheOtherAndCreatesBookingAndConversation() throws Exception {
        UUID categoryId = insertCategory();
        String customerSub = "kc-accept-it-cust-" + UUID.randomUUID();
        UUID customerId = insertUser(customerSub);
        UUID requestId = insertPublishedRequest(customerId, categoryId);

        String providerASub = "kc-accept-it-prov-a-" + UUID.randomUUID();
        UUID providerAId = insertEligibleProvider(providerASub, categoryId);
        String providerBSub = "kc-accept-it-prov-b-" + UUID.randomUUID();
        UUID providerBId = insertEligibleProvider(providerBSub, categoryId);

        UUID proposalAId = createProposal(providerASub, requestId, 15_000);
        UUID proposalBId = createProposal(providerBSub, requestId, 18_000);

        mockMvc.perform(post("/v1/proposals/{id}/accept", proposalAId).with(customerJwt(customerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(proposalStatus(proposalAId)).isEqualTo("ACCEPTED");
        assertThat(proposalStatus(proposalBId)).isEqualTo("SUPERSEDED");
        assertThat(requestStatus(requestId)).isEqualTo("CONFIRMED");

        List<Object[]> booking = awaitRow("""
                SELECT status FROM booking WHERE proposal_id = :proposalId
                """, proposalAId);
        assertThat(booking).as("Booking criada pelo listener assíncrono de bookings").hasSize(1);
        assertThat(booking.get(0)[0]).isEqualTo("CONFIRMED");

        List<Object[]> conversation = awaitRow("""
                SELECT customer_id, provider_id FROM conversation WHERE request_id = :requestId AND provider_id = :providerId
                """, requestId, "requestId", providerAId, "providerId");
        assertThat(conversation).as("Conversation criada pelo listener assíncrono de chat").hasSize(1);
        assertThat(conversation.get(0)[0]).isEqualTo(customerId);

        // A conversa não existe para o prestador cuja proposta foi superseded.
        assertThat(jdbcClient.sql("SELECT count(*) FROM conversation WHERE request_id = :requestId AND provider_id = :providerId")
                        .param("requestId", requestId)
                        .param("providerId", providerBId)
                        .query(Long.class)
                        .single())
                .isZero();
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

    private static RequestPostProcessor customerJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor providerJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"));
    }

    private String proposalStatus(UUID proposalId) {
        return jdbcClient.sql("SELECT status FROM proposal WHERE id = :id").param("id", proposalId).query(String.class).single();
    }

    private String requestStatus(UUID requestId) {
        return jdbcClient.sql("SELECT status FROM service_request WHERE id = :id").param("id", requestId).query(String.class).single();
    }

    /** Pequeno polling (listeners @ApplicationModuleListener são assíncronos por definição). */
    private List<Object[]> awaitRow(String sql, UUID param) throws InterruptedException {
        return awaitRow(sql, param, "proposalId", null, null);
    }

    private List<Object[]> awaitRow(String sql, UUID param1, String paramName1, UUID param2, String paramName2) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (true) {
            var spec = jdbcClient.sql(sql).param(paramName1, param1);
            if (param2 != null) {
                spec = spec.param(paramName2, param2);
            }
            List<Object[]> rows = spec.query((rs, rowNum) -> {
                int columnCount = rs.getMetaData().getColumnCount();
                Object[] row = new Object[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                return row;
            }).list();
            if (!rows.isEmpty() || System.currentTimeMillis() > deadline) {
                return rows;
            }
            Thread.sleep(100);
        }
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

    /**
     * {@code approval_status='APPROVED'} por {@code INSERT} direto é um
     * atalho de setup — a transição real ({@code PENDING → APPROVED} via
     * {@code PATCH /v1/admin/providers/{id}/approval}) é responsabilidade de
     * {@code modules/providers} (defeito C1, docs/ESTADO-DO-SISTEMA.md), com
     * o seu próprio teste de transição nesse módulo. {@code
     * approval_decided_by}/{@code approval_decided_at} só satisfazem aqui o
     * {@code CHECK chk_provider_profile_approval_decision_coherence} (V22);
     * reutiliza-se o {@code userId} do próprio prestador como autor fictício.
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
}
