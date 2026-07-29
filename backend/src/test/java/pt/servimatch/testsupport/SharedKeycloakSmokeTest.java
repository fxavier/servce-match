package pt.servimatch.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova isolada do mecanismo de autenticação real (contentor Keycloak +
 * realm importado, {@link SharedKeycloak}), independente da jornada
 * ponta-a-ponta: obtém tokens reais para os três utilizadores semeados e
 * verifica as claims de que o backend depende — {@code sub} (identidade,
 * base do provisionamento JIT), {@code aud} (validado por
 * {@code pt.servimatch.platform.security.AudienceValidator}) e
 * {@code realm_access.roles} (convertido em {@code ROLE_*} por
 * {@code KeycloakRoleConverter}).
 */
class SharedKeycloakSmokeTest {

    @Test
    void customerTokenCarriesTheClaimsTheBackendRequires() {
        assertClaims(SharedKeycloak.CUSTOMER_USERNAME, "CUSTOMER");
    }

    @Test
    void providerTokenCarriesTheClaimsTheBackendRequires() {
        assertClaims(SharedKeycloak.PROVIDER_USERNAME, "PROVIDER");
    }

    @Test
    void adminTokenCarriesTheClaimsTheBackendRequires() {
        assertClaims(SharedKeycloak.ADMIN_USERNAME, "ADMIN");
    }

    private void assertClaims(String username, String expectedRole) {
        String token = SharedKeycloak.accessToken(username, SharedKeycloak.SEEDED_PASSWORD);
        JsonNode claims = SharedKeycloak.decodePayload(token);

        assertThat(claims.get("sub").asText()).as("sub para " + username).isNotBlank();
        assertThat(claims.get("iss").asText()).as("iss para " + username).isEqualTo(SharedKeycloak.issuerUri());
        // AudienceValidator (pt.servimatch.platform.security) exige esta claim.
        assertThat(claims.get("aud").toString()).as("aud para " + username).contains("servimatch-backend");
        // KeycloakRoleConverter lê exatamente este caminho (servimatch.security.jwt.roles-claim, omissão realm_access.roles).
        assertThat(claims.path("realm_access").path("roles").toString())
                .as("realm_access.roles para " + username)
                .contains(expectedRole);
    }
}
