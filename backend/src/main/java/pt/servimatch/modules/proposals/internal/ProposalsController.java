package pt.servimatch.modules.proposals.internal;

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
import pt.servimatch.modules.proposals.internal.web.CreateProposalRequest;
import pt.servimatch.modules.proposals.internal.web.ProposalDto;
import pt.servimatch.modules.proposals.internal.web.ProposalPageDto;
import pt.servimatch.modules.users.UsersApi;

import java.net.URI;
import java.util.UUID;

/**
 * Controlador REST de {@code proposals} — ver {@code docs/api/openapi.yaml} (tag Proposals).
 *
 * <p>{@code @Lazy}: ver nota em {@code RequestsController}.
 */
@RestController
@Lazy
class ProposalsController {

    private final ProposalsService proposalsService;
    private final UsersApi usersApi;

    ProposalsController(ProposalsService proposalsService, UsersApi usersApi) {
        this.proposalsService = proposalsService;
        this.usersApi = usersApi;
    }

    @GetMapping("/v1/requests/{requestId}/proposals")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    ProposalPageDto listRequestProposals(Authentication authentication, @PathVariable UUID requestId,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(required = false, defaultValue = "20") int limit) {
        UUID viewerId = usersApi.ensureProvisioned(jwt(authentication));
        boolean isAdmin = hasRole(authentication, "ADMIN");
        return proposalsService.listForRequest(requestId, viewerId, isAdmin, cursor, Math.min(Math.max(limit, 1), 100));
    }

    @PostMapping("/v1/requests/{requestId}/proposals")
    @PreAuthorize("hasRole('PROVIDER')")
    ResponseEntity<ProposalDto> createProposal(Authentication authentication, @PathVariable UUID requestId,
                                                @Valid @RequestBody CreateProposalRequest request,
                                                UriComponentsBuilder uriBuilder) {
        UUID providerUserId = usersApi.ensureProvisioned(jwt(authentication));
        ProposalDto created = proposalsService.create(requestId, providerUserId, request);
        URI location = uriBuilder.path("/v1/requests/{requestId}/proposals/{id}")
                .buildAndExpand(requestId, created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/v1/proposals/me")
    @PreAuthorize("hasRole('PROVIDER')")
    ProposalPageDto listMyProposals(Authentication authentication,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(required = false, defaultValue = "20") int limit) {
        UUID userId = usersApi.ensureProvisioned(jwt(authentication));
        return proposalsService.listMine(userId, cursor, Math.min(Math.max(limit, 1), 100));
    }

    @PostMapping("/v1/proposals/{proposalId}/accept")
    @PreAuthorize("hasRole('CUSTOMER')")
    ProposalDto acceptProposal(Authentication authentication, @PathVariable UUID proposalId) {
        UUID customerId = usersApi.ensureProvisioned(jwt(authentication));
        return proposalsService.accept(proposalId, customerId);
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
