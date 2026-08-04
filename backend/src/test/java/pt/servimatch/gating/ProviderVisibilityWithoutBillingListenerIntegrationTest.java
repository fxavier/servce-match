package pt.servimatch.gating;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pt.servimatch.testsupport.SharedPostgis;
import pt.servimatch.testsupport.TestDatabase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reprodução mínima de um defeito de produção, distinto do que
 * {@link SubscriptionGatingAcrossModulesIntegrationTest} prova.
 *
 * <p><b>O que este teste NÃO faz, deliberadamente:</b> ao contrário de
 * {@code SubscriptionGatingAcrossModulesIntegrationTest#activateSubscription},
 * este teste nunca escreve {@code provider_profile.visibility_state} — porque
 * nenhum código de produção o faz. Não existe (e nunca existiu) um listener
 * {@code billing → providers} que traduza {@code SubscriptionActivated} para
 * essa coluna; {@code provider_profile.visibility_state} fica sempre no seu
 * valor por omissão ({@code 'HIDDEN'}, V4) em produção. Isto é confirmado por
 * {@code ProvidersApi.checkEligibility} (javadoc: "uma coluna sem nenhum
 * escritor em produção — o defeito que o ADR fecha") e pelo ADR-0011, que
 * corrigiu exatamente este módulo para deixar de ler essa coluna.
 *
 * <p><b>O que este teste provou (histórico, corrigido):</b> {@code matching}
 * e {@code search} não tinham sido corrigidos da mesma forma que
 * {@code providers} —
 * {@code EligibilityRepository} (SQL de {@code isEligible}/
 * {@code findEligibleProviderIds}) e {@code ProviderSearchRepository} (SQL de
 * {@code GET /v1/search/providers}) continuavam com o predicado
 * {@code p.visibility_state = 'VISIBLE'} na cláusula {@code WHERE}. Um
 * prestador real — aprovado, com subscrição {@code ACTIVE} tal como o
 * ciclo de vida de {@code billing} a produz de facto (ver
 * {@code DefaultSubscriptionLifecycle}), sem nenhuma manipulação adicional
 * de {@code visibility_state} — ficava permanentemente invisível na pesquisa
 * pública, apesar de reunir todas as condições de negócio para aparecer.
 * Era uma indisponibilidade total e silenciosa do produto (nenhum
 * prestador subscrito jamais aparecia em {@code GET
 * /v1/search/providers}), não um caso de fronteira.
 *
 * <p><b>Corrigido:</b> {@code EligibilityRepository}/
 * {@code ProviderSearchRepository} deixaram de filtrar por
 * {@code visibility_state} e passaram a compor a mesma verdade que
 * {@code ProvidersApi.checkEligibility} já usa
 * ({@code approval_status = 'APPROVED'} + o fragmento único
 * {@code billing.SubscriptionVisibilitySql.grantsVisibilityPredicate}, que
 * resolve {@code status ∈ {ACTIVE, PAST_DUE}} e {@code current_period_end}
 * a partir de {@code SubscriptionStatus.grantsVisibility()} — ADR-0011 §D4).
 * Reativado (removido {@code @Disabled}) como prova.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class ProviderVisibilityWithoutBillingListenerIntegrationTest {

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
    void providerWithApprovedProfileAndGenuinelyActiveSubscriptionAppearsInPublicSearch() throws Exception {
        UUID categoryId = insertCategory();
        UUID providerId = insertApprovedProviderCoveringLisbon(categoryId);

        // Estado exatamente como o ciclo de vida real de `billing` o produz
        // (ver DefaultSubscriptionLifecycle.activate) — sem tocar em
        // provider_profile.visibility_state, porque nada em produção o faz.
        activateSubscriptionAsBillingReallyDoes(providerId);

        JsonNode items = objectMapper.readTree(
                        mockMvc.perform(get("/v1/search/providers").param("categoryId", categoryId.toString()))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString())
                .get("items");

        assertThat(items)
                .as("prestador aprovado com subscrição ACTIVE genuína (produzida como billing "
                        + "realmente a produz, sem escrever visibility_state) deve aparecer em "
                        + "GET /v1/search/providers — se este assert falhar, matching/search ainda "
                        + "dependem da coluna morta que o ADR-0011 já identificou em providers")
                .anyMatch(item -> item.get("id").asText().equals(providerId.toString()));
    }

    private UUID insertCategory() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO category (id, slug, name) VALUES (:id, :slug, 'Categoria Visibilidade')")
                .param("id", id)
                .param("slug", "categoria-visibilidade-" + id)
                .update();
        return id;
    }

    /**
     * Aprovado, com categoria e cobertura por raio — nunca toca em
     * {@code visibility_state}.
     *
     * <p>{@code approval_status='APPROVED'} por {@code INSERT} direto é um
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
    private UUID insertApprovedProviderCoveringLisbon(UUID categoryId) {
        UUID userId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        String suffix = userId.toString();
        jdbcClient.sql("""
                        INSERT INTO users (id, keycloak_sub, email, display_name)
                        VALUES (:id, :sub, :email, 'Prestador Sem Listener')
                        """)
                .param("id", userId)
                .param("sub", "kc-vis-noop-" + suffix)
                .param("email", "vis-noop-" + suffix + "@example.test")
                .update();
        // Deliberadamente SEM a coluna visibility_state no INSERT: fica no
        // valor por omissão da migração V4 ('HIDDEN'), tal como em produção.
        jdbcClient.sql("""
                        INSERT INTO provider_profile (id, user_id, approval_status, approval_decided_by, approval_decided_at)
                        VALUES (:id, :userId, 'APPROVED', :userId, now())
                        """)
                .param("id", providerId)
                .param("userId", userId)
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
        return providerId;
    }

    private void activateSubscriptionAsBillingReallyDoes(UUID providerId) {
        UUID planId = TestDatabase.createPlan(jdbcClient, 4500);
        jdbcClient.sql("""
                        INSERT INTO subscription (provider_id, plan_id, status, gateway, current_period_start, current_period_end)
                        VALUES (:p, :plan, 'ACTIVE', 'stripe', now(), now() + interval '30 days')
                        """)
                .param("p", providerId)
                .param("plan", planId)
                .update();
    }
}
