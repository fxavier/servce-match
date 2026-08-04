package pt.servimatch.modules.chat;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pt.servimatch.testsupport.SharedPostgis;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova {@code GET /v1/conversations} contra Postgres real
 * ({@link SharedPostgis}): ordenação por {@code COALESCE(last_message_at,
 * created_at) DESC, id DESC} (V20), resolução em lote de
 * {@code counterpartName}/{@code requestTitle}, e autorização por
 * participante feita no {@code WHERE} — não com um {@code if} depois de
 * carregar tudo (um estranho não vê nenhuma das duas conversas, sem
 * exceção nenhuma, porque a query nunca as devolve).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class ListConversationsIntegrationTest {

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
    void listsOnlyTheAuthenticatedParticipantsConversationsMostRecentActivityFirst() throws Exception {
        UUID categoryId = insertCategory();
        String customerSub = "kc-list-conv-cust-" + UUID.randomUUID();
        UUID customerId = insertUser(customerSub, "Cliente Um");

        String providerASub = "kc-list-conv-prov-a-" + UUID.randomUUID();
        UUID providerAUserId = insertUser(providerASub, "Canalizações Silva");
        UUID providerAId = insertProvider(providerAUserId);

        String providerBSub = "kc-list-conv-prov-b-" + UUID.randomUUID();
        UUID providerBUserId = insertUser(providerBSub, "Mariana Pinturas");
        UUID providerBId = insertProvider(providerBUserId);

        UUID requestA = insertRequest(customerId, categoryId, "Fuga de água na cozinha");
        UUID requestB = insertRequest(customerId, categoryId, "Pintar sala e quarto");

        // Conversa B: acabada de nascer (proposta aceite), ainda sem mensagens —
        // criada primeiro para que a sua atividade (a própria criação) seja
        // cronologicamente anterior à de A, apesar de o pedido B em si ser
        // mais recente do que o pedido A.
        UUID conversationB = insertConversation(requestB, customerId, providerBId);
        Thread.sleep(5);

        // Conversa A: tem mensagens (last_message_at populado pelo trigger V20),
        // enviadas depois da criação de B — fica com a atividade mais recente.
        UUID conversationA = insertConversation(requestA, customerId, providerAId);
        insertMessage(conversationA, providerAUserId, "Bom dia! Posso passar amanhã.");
        insertMessage(conversationA, customerId, "Perfeito, fico à espera.");

        String response = mockMvc.perform(get("/v1/conversations").with(customerJwt(customerSub)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = objectMapper.readTree(response).get("items");

        assertThat(items).hasSize(2);
        // Conversa A tem atividade (última mensagem) mais recente que a criação de B -> A primeiro.
        assertThat(items.get(0).get("id").asText()).isEqualTo(conversationA.toString());
        assertThat(items.get(0).get("counterpartName").asText()).isEqualTo("Canalizações Silva");
        assertThat(items.get(0).get("requestTitle").asText()).isEqualTo("Fuga de água na cozinha");
        assertThat(items.get(0).get("lastMessagePreview").asText()).isEqualTo("Perfeito, fico à espera.");
        // unreadCount conta mensagens do prestador (sender <> customerId) ainda
        // não lidas pelo cliente — não depende de quem enviou a última mensagem
        // da conversa; last_read_by_customer_at nunca foi escrito (sem endpoint
        // de "marcar como lida" no contrato v1.0.0), por isso a única mensagem
        // do prestador conta como por ler.
        assertThat(items.get(0).get("unreadCount").asInt()).isEqualTo(1);

        assertThat(items.get(1).get("id").asText()).isEqualTo(conversationB.toString());
        assertThat(items.get(1).get("counterpartName").asText()).isEqualTo("Mariana Pinturas");
        assertThat(items.get(1).get("lastMessagePreview").isNull()).isTrue();
        assertThat(items.get(1).get("lastMessageAt").isNull()).isTrue();

        // Do lado do prestador A, unreadCount só conta mensagens enviadas pelo
        // cliente (sender <> viewerId) — a mensagem do próprio prestador nunca
        // conta como "por ler" para si mesmo. last_read_by_provider_at nunca
        // foi escrito (nenhum endpoint de "marcar como lida" no contrato v1.0.0).
        String providerResponse = mockMvc.perform(get("/v1/conversations").with(providerJwt(providerASub)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode providerItems = objectMapper.readTree(providerResponse).get("items");
        assertThat(providerItems).hasSize(1);
        assertThat(providerItems.get(0).get("counterpartName").asText()).isEqualTo("Cliente Um");
        assertThat(providerItems.get(0).get("unreadCount").asInt()).isEqualTo(1);

        // Um estranho não é participante de nenhuma das duas -> lista vazia, autorização no WHERE.
        String strangerSub = "kc-list-conv-stranger-" + UUID.randomUUID();
        insertUser(strangerSub, "Estranho");
        String strangerResponse = mockMvc.perform(get("/v1/conversations").with(customerJwt(strangerSub)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(strangerResponse).get("items")).isEmpty();
    }

    @Test
    void cursorPaginationNeverRepeatsOrSkipsARowAcrossPages() throws Exception {
        UUID categoryId = insertCategory();
        String customerSub = "kc-list-conv-cursor-cust-" + UUID.randomUUID();
        UUID customerId = insertUser(customerSub, "Cliente Cursor");

        java.util.List<UUID> conversationIds = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String providerSub = "kc-list-conv-cursor-prov-" + i + "-" + UUID.randomUUID();
            UUID providerUserId = insertUser(providerSub, "Prestador " + i);
            UUID providerId = insertProvider(providerUserId);
            UUID requestId = insertRequest(customerId, categoryId, "Pedido " + i);
            UUID conversationId = insertConversation(requestId, customerId, providerId);
            insertMessage(conversationId, providerUserId, "mensagem " + i);
            conversationIds.add(conversationId);
            // Separação determinística entre atividades: o cursor (CursorCodec,
            // duplicado do padrão já usado por chat.internal/requests.internal)
            // codifica o instante com precisão de milissegundo, menos fina que
            // a de TIMESTAMPTZ — sem esta folga, duas conversas cuja atividade
            // caia no mesmo milissegundo poderiam, em teoria, ficar dos dois
            // lados da fronteira do cursor. Não é um caso deste teste (que
            // prova estabilidade entre páginas em condições normais).
            Thread.sleep(5);
        }

        String firstPage = mockMvc.perform(get("/v1/conversations").param("limit", "2").with(customerJwt(customerSub)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode firstJson = objectMapper.readTree(firstPage);
        assertThat(firstJson.get("items")).hasSize(2);
        String nextCursor = firstJson.get("page").get("nextCursor").asText();
        assertThat(nextCursor).isNotBlank();

        String secondPage = mockMvc.perform(get("/v1/conversations")
                        .param("limit", "2")
                        .param("cursor", nextCursor)
                        .with(customerJwt(customerSub)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode secondJson = objectMapper.readTree(secondPage);
        assertThat(secondJson.get("items")).hasSize(1);

        java.util.Set<String> seenIds = new java.util.HashSet<>();
        firstJson.get("items").forEach(item -> seenIds.add(item.get("id").asText()));
        secondJson.get("items").forEach(item -> seenIds.add(item.get("id").asText()));
        assertThat(seenIds).hasSize(3);
        assertThat(seenIds).containsExactlyInAnyOrderElementsOf(conversationIds.stream().map(UUID::toString).toList());
    }

    private static RequestPostProcessor customerJwt(String subject) {
        return jwt().jwt(b -> b.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor providerJwt(String subject) {
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

    private UUID insertUser(String keycloakSub, String displayName) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO users (id, keycloak_sub, email, display_name)
                        VALUES (:id, :sub, :email, :displayName)
                        """)
                .param("id", id)
                .param("sub", keycloakSub)
                .param("email", keycloakSub + "@example.test")
                .param("displayName", displayName)
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
     * (V22); reutiliza-se o próprio {@code userId} do prestador como autor
     * fictício.
     */
    private UUID insertProvider(UUID userId) {
        UUID providerId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO provider_profile (id, user_id, approval_status, approval_decided_by, approval_decided_at, visibility_state)
                        VALUES (:id, :userId, 'APPROVED', :userId, now(), 'VISIBLE')
                        """)
                .param("id", providerId)
                .param("userId", userId)
                .update();
        return providerId;
    }

    private UUID insertRequest(UUID customerId, UUID categoryId, String title) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO service_request (
                            id, customer_id, category_id, title, address_line1, address_postal_code,
                            address_city, address_country, status, published_at
                        ) VALUES (
                            :id, :customerId, :categoryId, :title, 'Rua Teste', '1000-001',
                            'Lisboa', 'PT', 'PUBLISHED', now()
                        )
                        """)
                .param("id", id)
                .param("customerId", customerId)
                .param("categoryId", categoryId)
                .param("title", title)
                .update();
        return id;
    }

    private UUID insertConversation(UUID requestId, UUID customerId, UUID providerId) {
        return jdbcClient.sql("""
                        INSERT INTO conversation (request_id, customer_id, provider_id)
                        VALUES (:requestId, :customerId, :providerId)
                        RETURNING id
                        """)
                .param("requestId", requestId)
                .param("customerId", customerId)
                .param("providerId", providerId)
                .query(UUID.class)
                .single();
    }

    private void insertMessage(UUID conversationId, UUID senderId, String body) {
        jdbcClient.sql("""
                        INSERT INTO message (conversation_id, sender_id, body)
                        VALUES (:conversationId, :senderId, :body)
                        """)
                .param("conversationId", conversationId)
                .param("senderId", senderId)
                .param("body", body)
                .update();
    }
}
