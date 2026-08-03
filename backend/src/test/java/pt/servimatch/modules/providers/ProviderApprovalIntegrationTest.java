package pt.servimatch.modules.providers;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pt.servimatch.testsupport.SharedPostgis;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova contra Postgres real ({@link SharedPostgis}) de que
 * {@code provider_profile.approval_status} tem, a partir de agora, um
 * escritor de produção (defeito C1, {@code docs/ESTADO-DO-SISTEMA.md};
 * ADR-0011 D7; skill {@code estado-com-escritor}).
 *
 * <p>Segue à letra o procedimento da skill: o estado inicial
 * ({@code PENDING}) nasce do <b>caminho de produção</b> —
 * {@code GET /v1/providers/me}, que aciona
 * {@code ProvidersApi#ensureProvisioned} — nunca de um {@code INSERT}
 * direto com {@code approval_status} escolhido pelo teste; e a transição
 * para {@code APPROVED} passa pelo próprio
 * {@code PATCH /v1/admin/providers/{providerId}/approval}, nunca por
 * {@code UPDATE} SQL. A única leitura direta à base de dados neste ficheiro
 * é para <b>ler</b> o resultado (assert), nunca para o produzir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class ProviderApprovalIntegrationTest {

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
    void providerStartsPendingByProductionPathAndAdminApprovesIt() throws Exception {
        String providerSub = "kc-approval-happy-" + UUID.randomUUID();
        insertUser(providerSub);

        // 1. Caminho de produção: a PRIMEIRA GET /v1/providers/me provisiona o
        // perfil (ProvidersApi#ensureProvisioned) — nunca um INSERT do teste.
        UUID providerId = provisionProviderByProductionPath(providerSub);
        assertThat(currentApprovalStatus(providerId)).isEqualTo("PENDING");

        // 2. O próprio prestador (sem ROLE_ADMIN) não pode decidir a sua
        // própria aprovação — 403, sem tocar no estado.
        mockMvc.perform(patch("/v1/admin/providers/{id}/approval", providerId)
                        .with(providerJwt(providerSub))
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isForbidden());
        assertThat(currentApprovalStatus(providerId)).isEqualTo("PENDING");

        // 3. PATCH por ADMIN, transição válida PENDING → APPROVED.
        String adminSub = "kc-admin-happy-" + UUID.randomUUID();
        UUID adminUserId = insertUser(adminSub);

        mockMvc.perform(patch("/v1/admin/providers/{id}/approval", providerId)
                        .with(adminJwt(adminSub))
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(providerId.toString()))
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.decidedBy").value(adminUserId.toString()))
                .andExpect(jsonPath("$.decidedAt").exists());

        assertThat(currentApprovalStatus(providerId)).isEqualTo("APPROVED");

        // 4. Repetir a mesma decisão agora que já está APPROVED não é uma
        // transição da allowlist (só APPROVED→SUSPENDED o é) — 409 com o
        // estado atual no corpo, nada muda.
        mockMvc.perform(patch("/v1/admin/providers/{id}/approval", providerId)
                        .with(adminJwt(adminSub))
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("APPROVED")));
        assertThat(currentApprovalStatus(providerId)).isEqualTo("APPROVED");
    }

    @Test
    void rejectedDecisionWithoutReasonIs422AndLeavesStatusUnchanged() throws Exception {
        String providerSub = "kc-approval-422-" + UUID.randomUUID();
        insertUser(providerSub);
        UUID providerId = provisionProviderByProductionPath(providerSub);

        String adminSub = "kc-admin-422-" + UUID.randomUUID();
        insertUser(adminSub);

        mockMvc.perform(patch("/v1/admin/providers/{id}/approval", providerId)
                        .with(adminJwt(adminSub))
                        .contentType("application/json")
                        .content("""
                                {"decision":"REJECTED"}
                                """))
                .andExpect(status().isUnprocessableEntity());

        assertThat(currentApprovalStatus(providerId)).isEqualTo("PENDING");
    }

    @Test
    void suspendingAnApprovedProviderRequiresApprovedFirstAndRecordsTheReason() throws Exception {
        String providerSub = "kc-approval-suspend-" + UUID.randomUUID();
        insertUser(providerSub);
        UUID providerId = provisionProviderByProductionPath(providerSub);

        String adminSub = "kc-admin-suspend-" + UUID.randomUUID();
        insertUser(adminSub);

        // SUSPENDED direto a partir de PENDING não está na allowlist — 409.
        mockMvc.perform(patch("/v1/admin/providers/{id}/approval", providerId)
                        .with(adminJwt(adminSub))
                        .contentType("application/json")
                        .content("""
                                {"decision":"SUSPENDED","reason":"Motivo qualquer"}
                                """))
                .andExpect(status().isConflict());
        assertThat(currentApprovalStatus(providerId)).isEqualTo("PENDING");

        // PENDING → APPROVED, depois APPROVED → SUSPENDED com motivo: caminho válido.
        mockMvc.perform(patch("/v1/admin/providers/{id}/approval", providerId)
                        .with(adminJwt(adminSub))
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/v1/admin/providers/{id}/approval", providerId)
                        .with(adminJwt(adminSub))
                        .contentType("application/json")
                        .content("""
                                {"decision":"SUSPENDED","reason":"Reclamações de clientes"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.reason").value("Reclamações de clientes"));

        assertThat(currentApprovalStatus(providerId)).isEqualTo("SUSPENDED");
    }

    // ---------------------------------------------------------------- fixtures

    /** Aciona o provisionamento JIT pelo caminho de produção (nunca INSERT) e devolve o {@code provider_profile.id} do corpo da resposta. */
    private UUID provisionProviderByProductionPath(String providerSub) throws Exception {
        String body = mockMvc.perform(get("/v1/providers/me").with(providerJwt(providerSub)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    /** Leitura direta só para verificar o resultado — nunca para o produzir (skill {@code estado-com-escritor}). */
    private String currentApprovalStatus(UUID providerId) {
        return jdbcClient.sql("SELECT approval_status FROM provider_profile WHERE id = :id")
                .param("id", providerId)
                .query(String.class)
                .single();
    }

    private static RequestPostProcessor providerJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"));
    }

    private static RequestPostProcessor adminJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
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
}
