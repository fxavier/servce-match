package pt.servimatch.modules.providers;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova contra Postgres real ({@link SharedPostgis}) de três garantias que
 * um teste com repositório simulado não alcança:
 * <ol>
 *   <li>{@code GET /v1/providers/{id}} é <b>indistinguível</b> — mesmo corpo
 *       de 404 — para prestador inexistente, não aprovado e aprovado mas sem
 *       subscrição que conceda visibilidade (ADR-0011);</li>
 *   <li>{@code ratingDistribution} é produzida por uma única consulta
 *       agregada sobre {@code review} (tabela do módulo {@code reviews}),
 *       leitura entre módulos cuja autorização sob o ADR-0010 exige prova
 *       contra base de dados real — ver javadoc de
 *       {@code ProviderRepository};</li>
 *   <li>{@code PUT /v1/providers/me} rejeita categoria/região desconhecida
 *       com {@code 422} sem persistir nada (nem sequer o {@code headline}).</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class ProvidersControllerIntegrationTest {

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
    void publicProfileIs404WithTheSameBodyForNonexistentNotApprovedAndNotVisibleProviders() throws Exception {
        UUID notApprovedId = insertProvider("PENDING", false);
        UUID approvedWithoutSubscriptionId = insertProvider("APPROVED", false);

        // Mesmo type/title/detail RFC 9457 nos três casos — distinguir seria
        // oráculo de enumeração num endpoint público e indexável (ADR-0011).
        for (UUID id : new UUID[] {UUID.randomUUID(), notApprovedId, approvedWithoutSubscriptionId}) {
            mockMvc.perform(get("/v1/providers/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/not-found"))
                    .andExpect(jsonPath("$.detail").value("Prestador não encontrado."));
        }
    }

    @Test
    void publicProfileReturns200WithBioAndLocationNullWhenNotSet() throws Exception {
        UUID providerId = insertProvider("APPROVED", true);

        mockMvc.perform(get("/v1/providers/{id}", providerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.location").doesNotExist());
    }

    @Test
    void publicProfileRatingDistributionIsASingleAggregatedQueryOverReviewsTargetingTheUser() throws Exception {
        UUID categoryId = insertCategory();
        UUID customerId = insertUser("kc-rd-cust-" + UUID.randomUUID());
        UUID providerId = insertProvider("APPROVED", true);
        UUID providerUserId = jdbcClient.sql("SELECT user_id FROM provider_profile WHERE id = :id")
                .param("id", providerId).query(UUID.class).single();

        insertCompletedReview(customerId, providerUserId, categoryId, 5);
        insertCompletedReview(customerId, providerUserId, categoryId, 5);
        insertCompletedReview(customerId, providerUserId, categoryId, 3);

        mockMvc.perform(get("/v1/providers/{id}", providerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingDistribution.5").value(2))
                .andExpect(jsonPath("$.ratingDistribution.4").value(0))
                .andExpect(jsonPath("$.ratingDistribution.3").value(1))
                .andExpect(jsonPath("$.ratingDistribution.2").value(0))
                .andExpect(jsonPath("$.ratingDistribution.1").value(0));
    }

    @Test
    void updateMyProfileRejectsUnknownCategoryAndPersistsNothing() throws Exception {
        String providerSub = "kc-put-cat-" + UUID.randomUUID();
        insertUser(providerSub);
        UUID unknownCategoryId = UUID.randomUUID();

        mockMvc.perform(put("/v1/providers/me")
                        .with(providerJwt(providerSub))
                        .contentType("application/json")
                        .content("""
                                {"headline":"Nova headline","bio":"","categoryIds":["%s"],"regionCodes":[],"portfolioImageIds":[]}
                                """.formatted(unknownCategoryId)))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/v1/providers/me").with(providerJwt(providerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").doesNotExist());
    }

    @Test
    void updateMyProfileRejectsUnknownRegionCode() throws Exception {
        String providerSub = "kc-put-region-" + UUID.randomUUID();
        insertUser(providerSub);

        mockMvc.perform(put("/v1/providers/me")
                        .with(providerJwt(providerSub))
                        .contentType("application/json")
                        .content("""
                                {"headline":"H","bio":"","categoryIds":[],"regionCodes":["PT-NOT-A-REGION"],"portfolioImageIds":[]}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateMyProfileHappyPathReplacesZonesAndIsReflectedOnNextGet() throws Exception {
        String providerSub = "kc-put-ok-" + UUID.randomUUID();
        insertUser(providerSub);

        mockMvc.perform(put("/v1/providers/me")
                        .with(providerJwt(providerSub))
                        .contentType("application/json")
                        .content("""
                                {"headline":"Eletricista certificado","bio":"20 anos de experiência","categoryIds":[],"regionCodes":["PT-LIS","PT-PRT"],"portfolioImageIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Eletricista certificado"))
                .andExpect(jsonPath("$.zones[?(@.regionCode=='PT-LIS' && @.label=='Lisboa')]").exists())
                .andExpect(jsonPath("$.zones[?(@.regionCode=='PT-PRT' && @.label=='Porto')]").exists());

        mockMvc.perform(get("/v1/providers/me").with(providerJwt(providerSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("20 anos de experiência"))
                .andExpect(jsonPath("$.zones.length()").value(2));
    }

    // ---------------------------------------------------------------- fixtures

    private static org.springframework.test.web.servlet.request.RequestPostProcessor providerJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"));
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

    private UUID insertCategory() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO category (id, slug, name) VALUES (:id, :slug, 'Categoria Teste')")
                .param("id", id)
                .param("slug", "categoria-teste-" + id)
                .update();
        return id;
    }

    /**
     * Prestador com {@code approvalStatus} indicado e, opcionalmente, uma
     * subscrição ACTIVE dentro do período.
     *
     * <p>{@code approval_status='APPROVED'} por {@code INSERT} direto é um
     * atalho de setup (ADR-0011 D9) — a transição real
     * ({@code PENDING → APPROVED} via {@code PATCH
     * /v1/admin/providers/{id}/approval}) tem o seu teste próprio em
     * {@link ProviderApprovalIntegrationTest} e, atravessando pesquisa/inbox,
     * em
     * {@code pt.servimatch.gating.ProviderApprovalUnlocksSearchAndMatchingIntegrationTest}.
     * {@code approval_decided_by}/{@code approval_decided_at} ficam
     * {@code NULL} para {@code PENDING} (nunca houve decisão) e preenchidos
     * nos restantes casos só para satisfazer o {@code CHECK
     * chk_provider_profile_approval_decision_coherence} (V22); reutiliza-se
     * o próprio {@code userId} do prestador como autor fictício.
     */
    private UUID insertProvider(String approvalStatus, boolean withActiveSubscription) {
        UUID userId = insertUser("kc-provider-" + UUID.randomUUID());
        UUID providerId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO provider_profile (id, user_id, approval_status, approval_decided_by, approval_decided_at)
                        VALUES (:id, :userId, :approvalStatus,
                                CASE WHEN :approvalStatus = 'PENDING' THEN NULL ELSE :userId END,
                                CASE WHEN :approvalStatus = 'PENDING' THEN NULL ELSE now() END)
                        """)
                .param("id", providerId)
                .param("userId", userId)
                .param("approvalStatus", approvalStatus)
                .update();
        if (withActiveSubscription) {
            UUID planId = TestDatabase.createPlan(jdbcClient, 0);
            jdbcClient.sql("""
                            INSERT INTO subscription (provider_id, plan_id, status, gateway, current_period_start, current_period_end)
                            VALUES (:p, :plan, 'ACTIVE', 'stripe', now(), now() + interval '30 days')
                            """)
                    .param("p", providerId)
                    .param("plan", planId)
                    .update();
        }
        return providerId;
    }

    /**
     * Cria a cadeia mínima (pedido → proposta → marcação {@code COMPLETED})
     * exigida pelo trigger {@code enforce_review_requires_completed_booking}
     * (V10) e insere uma avaliação com o {@code rating} indicado, visando
     * {@code targetUserId}.
     */
    private void insertCompletedReview(UUID customerId, UUID targetUserId, UUID categoryId, int rating) {
        UUID requestId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO service_request (id, customer_id, category_id, title)
                        VALUES (:id, :customerId, :categoryId, 'Pedido de teste para avaliação')
                        """)
                .param("id", requestId).param("customerId", customerId).param("categoryId", categoryId).update();

        UUID providerId = jdbcClient.sql("SELECT id FROM provider_profile WHERE user_id = :userId")
                .param("userId", targetUserId).query(UUID.class).single();

        UUID proposalId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO proposal (id, request_id, provider_id, price_amount_cents, description)
                        VALUES (:id, :requestId, :providerId, 5000, 'Proposta de teste')
                        """)
                .param("id", proposalId).param("requestId", requestId).param("providerId", providerId).update();

        UUID bookingId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO booking (id, proposal_id, status, completed_at)
                        VALUES (:id, :proposalId, 'COMPLETED', now())
                        """)
                .param("id", bookingId).param("proposalId", proposalId).update();

        jdbcClient.sql("""
                        INSERT INTO review (booking_id, author_id, target_id, rating)
                        VALUES (:bookingId, :authorId, :targetId, :rating)
                        """)
                .param("bookingId", bookingId)
                .param("authorId", customerId)
                .param("targetId", targetUserId)
                .param("rating", rating)
                .update();
    }
}
