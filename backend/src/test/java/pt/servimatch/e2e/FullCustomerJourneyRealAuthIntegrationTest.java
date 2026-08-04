package pt.servimatch.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pt.servimatch.testsupport.SharedKeycloak;
import pt.servimatch.testsupport.SharedPostgis;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A jornada inteira do produto (CLAUDE.md, cenários obrigatórios de
 * {@code qa-e2e}), <b>ponta a ponta com autenticação real</b>: token obtido
 * de um Keycloak em contentor ({@link SharedKeycloak}) contra o realm
 * importado de {@code infra/keycloak/realm-servimatch.json}, verificado pela
 * cadeia de segurança real do backend (assinatura JWKS, {@code iss},
 * {@code aud}, conversão {@code realm_access.roles → ROLE_*}) — nunca um
 * {@code Authentication} sintético via {@code spring-security-test}.
 *
 * <p>Os testes de módulo (ver "já coberto" no relatório de entrega desta
 * onda) já provam cada transição isoladamente, com tokens sintéticos, mais
 * rápidos. O que falta e este teste cobre é a cadeia completa a atravessar
 * <b>todos</b> os módulos numa única sessão HTTP real: {@code requests} →
 * {@code proposals} → {@code bookings} (criada pelo listener assíncrono de
 * {@code ProposalAccepted}) → {@code chat} (idem) → {@code reviews}
 * bilateral.
 *
 * <p>Único cliente/prestador do realm semeado
 * ({@code customer.test@}/{@code provider.test@servimatch.pt}) — concorrência
 * entre propostas já está provada com PostGIS real e tokens sintéticos em
 * {@code AcceptProposalIntegrationTest}; não repetida aqui.
 *
 * <p>O prestador é pré-aprovado e subscrito diretamente por SQL antes de
 * qualquer chamada HTTP seguindo o mesmo padrão de
 * {@code RequestVisibilityIntegrationTest#insertEligibleProvider} — a única
 * diferença é que o {@code keycloak_sub} usado é o {@code sub} real do token
 * do Keycloak em contentor, não um valor sintético. Sem este pré-registo, o
 * primeiro pedido autenticado do prestador criaria um {@code ProviderProfile}
 * {@code PENDING}/{@code HIDDEN} via provisionamento JIT
 * ({@code ProvidersApi#ensureProvisioned}) e a inbox ficaria vazia — não é
 * isso que este teste quer provar (isso é o teste de <em>gating</em>,
 * {@code pt.servimatch.gating.SubscriptionGatingAcrossModulesIntegrationTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("requests-it")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullCustomerJourneyRealAuthIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgis::jdbcUrl);
        registry.add("spring.datasource.username", SharedPostgis::username);
        registry.add("spring.datasource.password", SharedPostgis::password);
        registry.add("servimatch.security.jwt.jwk-set-uri", SharedKeycloak::jwkSetUri);
        registry.add("servimatch.security.jwt.issuer-uri", SharedKeycloak::issuerUri);
    }

    @Test
    @Order(1)
    void theFullProductJourneyWorksEndToEndWithRealKeycloakTokens() throws Exception {
        String customerToken = SharedKeycloak.accessToken(SharedKeycloak.CUSTOMER_USERNAME, SharedKeycloak.SEEDED_PASSWORD);
        String providerToken = SharedKeycloak.accessToken(SharedKeycloak.PROVIDER_USERNAME, SharedKeycloak.SEEDED_PASSWORD);
        String providerSub = SharedKeycloak.decodePayload(providerToken).get("sub").asText();

        UUID categoryId = insertCategory();
        UUID providerId = seedEligibleProvider(providerSub, categoryId);

        // 1. Cliente publica um pedido (DRAFT -> PUBLISHED), com um token
        // Keycloak real: exercita a cadeia inteira de autenticação e o
        // provisionamento JIT do utilizador (UsersService#ensureProvisioned).
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("categoryId", categoryId.toString());
        createBody.put("title", "Fuga de água na cozinha");
        createBody.put("description", "Torneira a pingar sem parar desde ontem.");
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("line1", "Rua Teste 123");
        address.put("postalCode", "1000-001");
        address.put("city", "Lisboa");
        address.put("regionCode", "PT-11");
        address.put("country", "PT");
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("lat", 38.7169);
        location.put("lon", -9.1399);
        address.put("location", location);
        createBody.put("address", address);
        createBody.put("urgency", "NORMAL");

        JsonNode created = postAs(customerToken, "/v1/requests", createBody, HttpStatus.CREATED);
        UUID requestId = UUID.fromString(created.get("id").asText());
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");

        JsonNode published = postAs(customerToken, "/v1/requests/" + requestId + "/publish", null, HttpStatus.OK);
        assertThat(published.get("status").asText()).isEqualTo("PUBLISHED");

        // Provisionamento JIT do cliente já aconteceu (createRequest) — resolve o
        // users.id real para usar como targetId da avaliação bilateral mais à frente.
        String customerSub = SharedKeycloak.decodePayload(customerToken).get("sub").asText();
        UUID customerUserId = userIdForSub(customerSub);

        // 2. O prestador elegível vê o pedido na sua inbox (mesmo predicado de
        // ADR-0004 §10.3: categoria + cobertura geográfica + subscrição ativa).
        JsonNode inbox = getAs(providerToken, "/v1/providers/me/requests", HttpStatus.OK);
        assertThat(inbox.get("items")).anyMatch(item -> item.get("id").asText().equals(requestId.toString()));

        // 3. Envia proposta.
        Map<String, Object> proposalBody = new LinkedHashMap<>();
        Map<String, Object> price = new LinkedHashMap<>();
        price.put("amountCents", 15_000);
        price.put("currency", "EUR");
        proposalBody.put("price", price);
        proposalBody.put("description", "Reparo completo, material incluído.");
        proposalBody.put("leadTimeDays", 2);

        JsonNode proposal = postAs(providerToken, "/v1/requests/" + requestId + "/proposals", proposalBody, HttpStatus.CREATED);
        UUID proposalId = UUID.fromString(proposal.get("id").asText());
        assertThat(proposal.get("status").asText()).isEqualTo("SENT");

        JsonNode proposalsSeenByCustomer = getAs(customerToken, "/v1/requests/" + requestId + "/proposals", HttpStatus.OK);
        assertThat(proposalsSeenByCustomer.get("items")).anyMatch(item -> item.get("id").asText().equals(proposalId.toString()));

        // 4. O cliente aceita a proposta.
        JsonNode accepted = postAs(customerToken, "/v1/proposals/" + proposalId + "/accept", null, HttpStatus.OK);
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(requestStatus(requestId)).isEqualTo("CONFIRMED");

        // 5. Booking e Conversation nascem de ProposalAccepted, via listeners
        // @ApplicationModuleListener — assíncronos por definição.
        UUID bookingId = awaitBookingForProposal(proposalId);
        UUID conversationId = awaitConversation(requestId, providerId);

        // 6. Mensagem trocada na conversa criada.
        JsonNode initialMessages = getAs(customerToken, "/v1/conversations/" + conversationId + "/messages", HttpStatus.OK);
        assertThat(initialMessages.get("items")).isEmpty();

        postAs(customerToken, "/v1/conversations/" + conversationId + "/messages",
                Map.of("body", "Olá! Pode vir amanhã de manhã?"), HttpStatus.CREATED);
        postAs(providerToken, "/v1/conversations/" + conversationId + "/messages",
                Map.of("body", "Sem problema, chego às 9h."), HttpStatus.CREATED);

        JsonNode messages = getAs(providerToken, "/v1/conversations/" + conversationId + "/messages", HttpStatus.OK);
        assertThat(messages.get("items")).hasSize(2);

        // 7. Conclui a marcação.
        JsonNode completed = postAs(providerToken, "/v1/bookings/" + bookingId + "/complete", null, HttpStatus.OK);
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");

        // 8. Avaliação bilateral — só permitida com Booking COMPLETED.
        JsonNode customerReviewsProvider = postAs(customerToken, "/v1/reviews",
                Map.of("bookingId", bookingId.toString(), "targetId", providerUserId(providerId).toString(),
                        "rating", 5, "comment", "Excelente, rápido e profissional."),
                HttpStatus.CREATED);
        assertThat(customerReviewsProvider.get("rating").asInt()).isEqualTo(5);

        JsonNode providerReviewsCustomer = postAs(providerToken, "/v1/reviews",
                Map.of("bookingId", bookingId.toString(), "targetId", customerUserId.toString(),
                        "rating", 4, "comment", "Cliente claro e pontual."),
                HttpStatus.CREATED);
        assertThat(providerReviewsCustomer.get("rating").asInt()).isEqualTo(4);
    }

    // ---------------------------------------------------------------- HTTP helpers (Bearer real, sem MockMvc)

    private JsonNode postAs(String token, String path, Object body, HttpStatus expectedStatus) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body == null ? "" : MAPPER.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + path, HttpMethod.POST, entity, String.class);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody()).isEqualTo(expectedStatus);
        return response.getBody() == null || response.getBody().isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(response.getBody());
    }

    private JsonNode getAs(String token, String path, HttpStatus expectedStatus) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).as("GET " + path + " -> " + response.getBody()).isEqualTo(expectedStatus);
        return MAPPER.readTree(response.getBody());
    }

    // ---------------------------------------------------------------- Fixtures (SQL direto, mesmo padrão de RequestVisibilityIntegrationTest)

    private UUID insertCategory() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO category (id, slug, name) VALUES (:id, :slug, 'Canalização')")
                .param("id", id)
                .param("slug", "canalizacao-" + id)
                .update();
        return id;
    }

    /**
     * Prestador aprovado, visível, com subscrição ativa, categoria e
     * cobertura de raio sobre Lisboa.
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
     * (V22); reutiliza-se o próprio {@code userId} do prestador como autor
     * fictício.
     */
    private UUID seedEligibleProvider(String keycloakSub, UUID categoryId) {
        UUID userId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO users (id, keycloak_sub, email, display_name)
                        VALUES (:id, :sub, :email, 'Prestador Teste')
                        ON CONFLICT (keycloak_sub) DO NOTHING
                        """)
                .param("id", userId)
                .param("sub", keycloakSub)
                .param("email", "provider.test@servimatch.pt")
                .update();
        userId = userIdForSub(keycloakSub);

        UUID providerId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO provider_profile (id, user_id, approval_status, approval_decided_by, approval_decided_at, visibility_state)
                        VALUES (:id, :userId, 'APPROVED', :userId, now(), 'VISIBLE')
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
        UUID planId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO subscription_plan (id, code, name, price_amount_cents)
                        VALUES (:id, :code, 'Plano Teste', 0)
                        """)
                .param("id", planId)
                .param("code", "test-plan-" + planId)
                .update();
        jdbcClient.sql("""
                        INSERT INTO subscription (provider_id, plan_id, status, gateway, current_period_start, current_period_end)
                        VALUES (:p, :plan, 'ACTIVE', 'stripe', now(), now() + interval '30 days')
                        """)
                .param("p", providerId)
                .param("plan", planId)
                .update();
        return providerId;
    }

    private UUID userIdForSub(String keycloakSub) {
        return jdbcClient.sql("SELECT id FROM users WHERE keycloak_sub = :sub")
                .param("sub", keycloakSub)
                .query(UUID.class)
                .single();
    }

    private UUID providerUserId(UUID providerId) {
        return jdbcClient.sql("SELECT user_id FROM provider_profile WHERE id = :id")
                .param("id", providerId)
                .query(UUID.class)
                .single();
    }

    private String requestStatus(UUID requestId) {
        return jdbcClient.sql("SELECT status FROM service_request WHERE id = :id").param("id", requestId).query(String.class).single();
    }

    /** Booking nasce do listener assíncrono {@code bookings.internal.ProposalAcceptedListener}. */
    private UUID awaitBookingForProposal(UUID proposalId) {
        return Awaitility.await("Booking criada pelo listener de ProposalAccepted")
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> jdbcClient.sql("SELECT id FROM booking WHERE proposal_id = :p AND status = 'CONFIRMED'")
                                .param("p", proposalId)
                                .query(UUID.class)
                                .optional(),
                        java.util.Optional::isPresent)
                .orElseThrow();
    }

    /** Conversation nasce do listener assíncrono {@code chat.internal.ProposalAcceptedListener}. */
    private UUID awaitConversation(UUID requestId, UUID providerId) {
        return Awaitility.await("Conversation criada pelo listener de ProposalAccepted")
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> jdbcClient.sql("SELECT id FROM conversation WHERE request_id = :r AND provider_id = :p")
                                .param("r", requestId)
                                .param("p", providerId)
                                .query(UUID.class)
                                .optional(),
                        java.util.Optional::isPresent)
                .orElseThrow();
    }
}
