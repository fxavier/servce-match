package pt.servimatch.modules.providers.internal;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.providers.internal.web.ProviderApprovalDto;
import pt.servimatch.modules.providers.internal.web.ProviderProfileDto;
import pt.servimatch.modules.providers.internal.web.UpdateProviderApprovalRequest;
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

    /**
     * {@code PATCH /v1/admin/providers/{providerId}/approval}: skill
     * {@code admin-moderation-endpoint}. {@code @PreAuthorize} corre
     * <b>antes</b> de {@link ProvidersService#decideApproval} carregar o
     * prestador (a anotação é avaliada pelo proxy CGLIB antes de o corpo do
     * método executar), para não formar um oráculo {@code 403}/{@code 404}
     * entre "sem role" e "prestador inexistente" — mesmo padrão de
     * visibilidade dos outros métodos deste controlador
     * ({@code getMyProviderProfile}, {@code updateMyProviderProfile}):
     * {@code package-private}, não {@code public} — CGLIB, ao contrário de
     * um proxy JDK por interface, consegue interceder métodos
     * package-private porque a subclasse gerada fica no mesmo pacote.
     *
     * <p>{@code decidedBy} é o {@code users.id} do administrador, resolvido
     * pelo {@code sub} do próprio JWT — nunca aceite no corpo do pedido
     * (o contrato, {@code UpdateProviderApproval}, também não o expõe).
     * {@code Idempotency-Key} (contrato) é honrado de forma transparente
     * pelo {@code IdempotencyFilter} de {@code platform/idempotency}, já
     * registado na cadeia de filtros para {@code PATCH} — nenhum código
     * adicional é preciso aqui.
     */
    @PatchMapping("/v1/admin/providers/{providerId}/approval")
    @PreAuthorize("hasRole('ADMIN')")
    ProviderApprovalDto decideProviderApproval(Authentication authentication,
                                                @PathVariable UUID providerId,
                                                @Valid @RequestBody UpdateProviderApprovalRequest request) {
        UUID adminUserId = usersApi.ensureProvisioned(jwt(authentication));
        return providersService.decideApproval(providerId, adminUserId, request.decision(), request.reason());
    }

    private static Jwt jwt(Authentication authentication) {
        return ((JwtAuthenticationToken) authentication).getToken();
    }
}
