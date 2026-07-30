package pt.servimatch.modules.providers.internal;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.providers.internal.web.ProviderProfileDto;
import pt.servimatch.modules.providers.internal.web.UpdateProviderProfileRequest;
import pt.servimatch.modules.users.UsersApi;

import java.util.UUID;

/**
 * Controlador REST de {@code providers} — ver {@code docs/api/openapi.yaml}
 * (tag "Providers & Search"). {@code GET /v1/providers/{providerId}} é
 * público ({@code security: []}); a precedência sobre
 * {@code /v1/providers/me} é resolvida pelo {@code SecurityConfig}
 * ({@code AUTHENTICATED_BEFORE_PUBLIC_GET_ENDPOINTS} — a autorização, não o
 * roteamento MVC, é o que precisa da ordem, ver o seu javadoc) e, do lado do
 * roteamento em si, pela comparação de especificidade de padrões do Spring
 * MVC (um segmento literal vence sempre um {@code {providerId}} template,
 * independentemente da ordem de declaração dos métodos aqui).
 *
 * <p>{@code @Lazy}: ver nota em
 * {@code pt.servimatch.modules.requests.internal.RequestsController}.
 */
@RestController
@Lazy
class ProvidersController {

    private final ProvidersService providersService;
    private final UsersApi usersApi;

    ProvidersController(ProvidersService providersService, UsersApi usersApi) {
        this.providersService = providersService;
        this.usersApi = usersApi;
    }

    @GetMapping("/v1/providers/{providerId}")
    ProviderProfileDto getProvider(@PathVariable UUID providerId) {
        return providersService.getPublicProfile(providerId);
    }

    @GetMapping("/v1/providers/me")
    @PreAuthorize("hasRole('PROVIDER')")
    ProviderProfileDto getMyProviderProfile(Authentication authentication) {
        UUID providerId = providersService.ensureProvisioned(usersApi.ensureProvisioned(jwt(authentication)));
        return providersService.getMyProfile(providerId);
    }

    @PutMapping("/v1/providers/me")
    @PreAuthorize("hasRole('PROVIDER')")
    ProviderProfileDto updateMyProviderProfile(Authentication authentication,
                                                @Valid @RequestBody UpdateProviderProfileRequest request) {
        UUID providerId = providersService.ensureProvisioned(usersApi.ensureProvisioned(jwt(authentication)));
        return providersService.updateMyProfile(providerId, request);
    }

    private static Jwt jwt(Authentication authentication) {
        return ((JwtAuthenticationToken) authentication).getToken();
    }
}
