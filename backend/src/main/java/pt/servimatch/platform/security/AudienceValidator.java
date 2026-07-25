package pt.servimatch.platform.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Valida a claim {@code aud} do access token contra a audiência esperada do
 * backend (ADR-0002: "validação de JWT contra o JWKS do Keycloak, com
 * verificação de iss, aud e exp"). A verificação de {@code iss}/{@code exp}
 * é feita por {@link org.springframework.security.oauth2.jwt.JwtValidators#createDefaultWithIssuer(String)};
 * esta classe cobre apenas o que falta.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE =
            new OAuth2Error("invalid_token", "O token não contém a audiência esperada.", null);

    private final String expectedAudience;

    public AudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience() != null && token.getAudience().contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
