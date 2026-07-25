package pt.servimatch.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do backend como OAuth2 Resource Server (ADR-0002/0009).
 *
 * <p>{@code jwkSetUri} aponta diretamente para o endpoint JWKS do realm
 * Keycloak (evita uma chamada de descoberta OIDC síncrona no arranque da
 * aplicação — {@link org.springframework.security.oauth2.jwt.NimbusJwtDecoder#withJwkSetUri}
 * só liga na primeira validação de token, não no arranque). {@code issuerUri}
 * é usado apenas para validar a claim {@code iss} do token, não para
 * descoberta.
 */
@ConfigurationProperties(prefix = "servimatch.security.jwt")
public record SecurityProperties(
        String jwkSetUri,
        String issuerUri,
        String expectedAudience,
        String rolesClaim
) {
    public SecurityProperties {
        if (rolesClaim == null || rolesClaim.isBlank()) {
            rolesClaim = "realm_access.roles";
        }
    }
}
