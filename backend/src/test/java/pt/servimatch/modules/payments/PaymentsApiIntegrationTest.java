package pt.servimatch.modules.payments;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
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
import pt.servimatch.testsupport.SharedPostgis;
import pt.servimatch.testsupport.TestDatabase;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração HTTP ponta-a-ponta dos três endpoints do contrato
 * (listSubscriptionPlans, createSubscription, paymentWebhook), contra
 * Postgres real ({@link SharedPostgis}). O checkout Stripe usa um servidor
 * HTTP local (JDK {@link HttpServer}) como stub — nunca a API real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("payments-it")
class PaymentsApiIntegrationTest {

    private static final String STRIPE_WEBHOOK_SECRET = "whsec_api_test_secret";

    private static HttpServer stripeStub;
    private static String stripeStubBaseUrl;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgis::jdbcUrl);
        registry.add("spring.datasource.username", SharedPostgis::username);
        registry.add("spring.datasource.password", SharedPostgis::password);
        registry.add("STRIPE_WEBHOOK_SECRET", () -> STRIPE_WEBHOOK_SECRET);
        registry.add("STRIPE_BASE_URL", () -> stripeStubBaseUrl());
    }

    private static synchronized String stripeStubBaseUrl() {
        if (stripeStub == null) {
            startStripeStub();
        }
        return stripeStubBaseUrl;
    }

    private static void startStripeStub() {
        try {
            stripeStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            stripeStub.createContext("/v1/checkout/sessions", exchange -> {
                String response = "{\"id\":\"cs_api_test\",\"url\":\"https://checkout.example.test/cs_api_test\"}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            stripeStub.start();
            stripeStubBaseUrl = "http://localhost:" + stripeStub.getAddress().getPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopStripeStub() {
        if (stripeStub != null) {
            stripeStub.stop(0);
        }
    }

    @Test
    void listSubscriptionPlansIsPublicAndReturnsSeededPlan() throws Exception {
        UUID planId = TestDatabase.createPlan(jdbcClient, 9900);

        mockMvc.perform(get("/v1/subscription-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + planId + "')].price.amountCents").value(9900));
    }

    @Test
    void createSubscriptionWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + UUID.randomUUID() + "\",\"gateway\":\"stripe\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createSubscriptionWithWrongRoleReturns403() throws Exception {
        mockMvc.perform(post("/v1/subscriptions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + UUID.randomUUID() + "\",\"gateway\":\"stripe\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSubscriptionHappyPathReturnsPendingCheckout() throws Exception {
        UUID planId = TestDatabase.createPlan(jdbcClient, 4500);
        String keycloakSub = "kc-api-test-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO users (id, keycloak_sub, email, display_name) VALUES (:id, :sub, :email, 'API Test')")
                .param("id", userId).param("sub", keycloakSub).param("email", keycloakSub + "@example.test").update();
        jdbcClient.sql("INSERT INTO provider_profile (id, user_id) VALUES (:id, :userId)")
                .param("id", UUID.randomUUID()).param("userId", userId).update();

        mockMvc.perform(post("/v1/subscriptions")
                        .with(jwt().jwt(builder -> builder.subject(keycloakSub)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + planId + "\",\"gateway\":\"stripe\",\"returnUrl\":\"https://app.servimatch.pt/x\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.example.test/cs_api_test"));
    }

    @Test
    void getMySubscriptionWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/v1/subscriptions/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMySubscriptionWithWrongRoleReturns403() throws Exception {
        mockMvc.perform(get("/v1/subscriptions/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMySubscriptionForAuthenticatedProviderWithoutProfileReturns404() throws Exception {
        // Role PROVIDER no token, mas sem provider_profile correspondente na base
        // (o ProviderAccountResolver não encontra nada) — o contrato só documenta
        // 404 para este endpoint, não 422; tratado como "sem subscrição".
        String keycloakSub = "kc-no-profile-" + UUID.randomUUID();

        mockMvc.perform(get("/v1/subscriptions/me")
                        .with(jwt().jwt(builder -> builder.subject(keycloakSub)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/not-found"));
    }

    @Test
    void getMySubscriptionReturns404WhenProviderNeverSubscribed() throws Exception {
        String keycloakSub = registerProvider();

        mockMvc.perform(get("/v1/subscriptions/me")
                        .with(jwt().jwt(builder -> builder.subject(keycloakSub)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/not-found"));
    }

    @Test
    void getMySubscriptionReturnsMostRecentSubscriptionEvenWhenOnlyTerminalHistoryExists() throws Exception {
        // Contrato: "subscrição mais recente ... em qualquer estado ... não só a
        // ativa"; 404 é só para "nunca subscreveu". Um histórico só-terminal
        // ainda é uma resposta 200 com esse estado, não 404.
        UUID planId = TestDatabase.createPlan(jdbcClient, 4500);
        String keycloakSub = registerProvider();
        UUID providerId = providerIdFor(keycloakSub);
        UUID subscriptionId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO subscription (id, provider_id, plan_id, status, gateway)
                        VALUES (:id, :providerId, :planId, 'CANCELLED', 'stripe')
                        """)
                .param("id", subscriptionId).param("providerId", providerId).param("planId", planId).update();

        mockMvc.perform(get("/v1/subscriptions/me")
                        .with(jwt().jwt(builder -> builder.subject(keycloakSub)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.id").value(subscriptionId.toString()));
    }

    @Test
    void getMySubscriptionReturnsActiveSubscriptionWithPeriod() throws Exception {
        UUID planId = TestDatabase.createPlan(jdbcClient, 4500);
        String keycloakSub = registerProvider();
        UUID providerId = providerIdFor(keycloakSub);
        UUID subscriptionId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO subscription (id, provider_id, plan_id, status, gateway, current_period_start, current_period_end)
                        VALUES (:id, :providerId, :planId, 'ACTIVE', 'stripe', now(), now() + interval '30 days')
                        """)
                .param("id", subscriptionId).param("providerId", providerId).param("planId", planId).update();

        mockMvc.perform(get("/v1/subscriptions/me")
                        .with(jwt().jwt(builder -> builder.subject(keycloakSub)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currentPeriodEnd").exists());
    }

    @Test
    void getMySubscriptionReturnsPastDueSubscription() throws Exception {
        UUID planId = TestDatabase.createPlan(jdbcClient, 4500);
        String keycloakSub = registerProvider();
        UUID providerId = providerIdFor(keycloakSub);
        UUID subscriptionId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO subscription (id, provider_id, plan_id, status, gateway, current_period_start, current_period_end)
                        VALUES (:id, :providerId, :planId, 'PAST_DUE', 'stripe', now() - interval '35 days', now() - interval '5 days')
                        """)
                .param("id", subscriptionId).param("providerId", providerId).param("planId", planId).update();

        mockMvc.perform(get("/v1/subscriptions/me")
                        .with(jwt().jwt(builder -> builder.subject(keycloakSub)).authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAST_DUE"));
    }

    private String registerProvider() {
        String keycloakSub = "kc-api-me-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO users (id, keycloak_sub, email, display_name) VALUES (:id, :sub, :email, 'API Test')")
                .param("id", userId).param("sub", keycloakSub).param("email", keycloakSub + "@example.test").update();
        jdbcClient.sql("INSERT INTO provider_profile (id, user_id) VALUES (:id, :userId)")
                .param("id", UUID.randomUUID()).param("userId", userId).update();
        return keycloakSub;
    }

    private UUID providerIdFor(String keycloakSub) {
        return jdbcClient.sql("""
                        SELECT pp.id FROM provider_profile pp JOIN users u ON u.id = pp.user_id WHERE u.keycloak_sub = :sub
                        """)
                .param("sub", keycloakSub)
                .query(UUID.class)
                .single();
    }

    @Test
    void webhookWithInvalidSignatureReturns401ProblemDetails() throws Exception {
        String body = "{\"id\":\"evt_api_invalid\",\"type\":\"checkout.session.completed\",\"created\":" + Instant.now().getEpochSecond() + ",\"data\":{\"object\":{}}}";

        mockMvc.perform(post("/v1/webhooks/payments/stripe")
                        .header("Stripe-Signature", "t=" + Instant.now().getEpochSecond() + ",v1=not-a-real-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/unauthenticated"));
    }

    @Test
    void webhookDuplicateDeliveryIsANoOpOnSecondCall() throws Exception {
        String eventId = "evt_api_dup_" + UUID.randomUUID();
        String body = "{\"id\":\"" + eventId + "\",\"type\":\"charge.dispute.created\",\"created\":" + Instant.now().getEpochSecond() + ",\"data\":{\"object\":{}}}";
        long timestamp = Instant.now().getEpochSecond();
        String signature = "t=" + timestamp + ",v1=" + hmac(timestamp + "." + body);

        mockMvc.perform(post("/v1/webhooks/payments/stripe")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/v1/webhooks/payments/stripe")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        Long count = jdbcClient.sql("SELECT count(*) FROM payment_gateway_event WHERE gateway = 'stripe' AND raw_event_id = :eventId")
                .param("eventId", eventId)
                .query(Long.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1L);
    }

    @Test
    void webhookMalformedBodyReturns400NotServerError() throws Exception {
        String body = "not json at all {{{";
        long timestamp = Instant.now().getEpochSecond();
        String signature = "t=" + timestamp + ",v1=" + hmac(timestamp + "." + body);

        mockMvc.perform(post("/v1/webhooks/payments/stripe")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private static String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(STRIPE_WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
