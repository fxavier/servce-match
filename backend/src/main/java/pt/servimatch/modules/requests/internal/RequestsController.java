package pt.servimatch.modules.requests.internal;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.requests.internal.web.CreateServiceRequestRequest;
import pt.servimatch.modules.requests.internal.web.ServiceRequestDto;
import pt.servimatch.modules.requests.internal.web.ServiceRequestPageDto;
import pt.servimatch.modules.users.UsersApi;

import java.net.URI;
import java.util.UUID;

/**
 * Controlador REST de {@code requests} — ver {@code docs/api/openapi.yaml}
 * (tags Requests, e {@code listProviderInbox} embora rotulado "Providers &
 * Search" no contrato: fica aqui porque devolve {@code ServiceRequest} —
 * dado que este módulo é o único dono da tabela {@code service_request},
 * ver relatório de entrega).
 *
 * <p>{@code @Lazy}: ver nota em
 * {@code pt.servimatch.modules.users.internal.UserRepository} — aqui
 * necessário também no controlador, porque o Spring MVC teria de o
 * instanciar (e, em cascata, o serviço e o repositório) já no arranque do
 * contexto para o registar no {@code RequestMappingHandlerMapping} caso não
 * fosse lazy; com {@code @Lazy} isso só acontece no primeiro pedido HTTP
 * real (comportamento oficialmente suportado pelo Spring Boot, o mesmo que
 * {@code spring.main.lazy-initialization=true} usa para todos os beans).
 */
@RestController
@Lazy
class RequestsController {

    private final RequestsService requestsService;
    private final UsersApi usersApi;
    private final ProvidersApi providersApi;

    RequestsController(RequestsService requestsService, UsersApi usersApi, ProvidersApi providersApi) {
        this.requestsService = requestsService;
        this.usersApi = usersApi;
        this.providersApi = providersApi;
    }

    @PostMapping("/v1/requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    ResponseEntity<ServiceRequestDto> createRequest(Authentication authentication,
                                                     @Valid @RequestBody CreateServiceRequestRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        UUID customerId = usersApi.ensureProvisioned(jwt(authentication));
        ServiceRequestDto created = requestsService.createDraft(customerId, request);
        URI location = uriBuilder.path("/v1/requests/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/v1/requests/{requestId}")
    ServiceRequestDto getRequest(Authentication authentication, @PathVariable UUID requestId) {
        UUID viewerId = usersApi.ensureProvisioned(jwt(authentication));
        boolean isAdmin = hasRole(authentication, "ADMIN");
        boolean isProvider = hasRole(authentication, "PROVIDER");
        return requestsService.getForViewer(requestId, viewerId, isProvider, isAdmin);
    }

    @PostMapping("/v1/requests/{requestId}/publish")
    @PreAuthorize("hasRole('CUSTOMER')")
    ServiceRequestDto publishRequest(Authentication authentication, @PathVariable UUID requestId) {
        UUID ownerId = usersApi.ensureProvisioned(jwt(authentication));
        return requestsService.publish(requestId, ownerId);
    }

    @GetMapping("/v1/providers/me/requests")
    @PreAuthorize("hasRole('PROVIDER')")
    ServiceRequestPageDto listProviderInbox(Authentication authentication,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false, defaultValue = "20") int limit) {
        UUID userId = usersApi.ensureProvisioned(jwt(authentication));
        UUID providerId = providersApi.ensureProvisioned(userId);
        ProvidersApi.ProviderEligibility eligibility = providersApi.checkEligibility(providerId).orElseThrow();
        if (!eligibility.isEligible()) {
            throw Problems.forbidden("Subscrição inativa ou perfil de prestador não aprovado.");
        }
        return requestsService.listInbox(status, cursor, Math.min(Math.max(limit, 1), 100));
    }

    private static Jwt jwt(Authentication authentication) {
        return ((JwtAuthenticationToken) authentication).getToken();
    }

    private static boolean hasRole(Authentication authentication, String role) {
        String authority = "ROLE_" + role;
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
