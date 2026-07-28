package pt.servimatch.modules.notifications.internal;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.notifications.internal.web.RegisterDeviceTokenRequest;
import pt.servimatch.modules.users.UsersApi;

import java.util.UUID;

/**
 * {@code POST}/{@code DELETE /v1/device-tokens} (operationId
 * {@code registerDeviceToken}/{@code deleteDeviceToken}). Sem
 * {@code @PreAuthorize}: qualquer principal autenticado pode registar/remover
 * o token do seu próprio dispositivo (o contrato não restringe por role); a
 * fronteira real é {@code userId} = dono do token, aplicada em
 * {@link DeviceTokenService}.
 *
 * <p>{@code @Lazy}: ver nota em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@RestController
@Lazy
class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;
    private final UsersApi usersApi;

    DeviceTokenController(DeviceTokenService deviceTokenService, UsersApi usersApi) {
        this.deviceTokenService = deviceTokenService;
        this.usersApi = usersApi;
    }

    @PostMapping("/v1/device-tokens")
    ResponseEntity<Void> registerDeviceToken(Authentication authentication, @Valid @RequestBody RegisterDeviceTokenRequest request) {
        UUID userId = usersApi.ensureProvisioned(jwt(authentication));
        deviceTokenService.register(userId, request.token(), request.platform(), request.appVersion());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/v1/device-tokens/{token}")
    ResponseEntity<Void> deleteDeviceToken(Authentication authentication, @PathVariable String token) {
        UUID userId = usersApi.ensureProvisioned(jwt(authentication));
        deviceTokenService.delete(userId, token);
        return ResponseEntity.noContent().build();
    }

    private static Jwt jwt(Authentication authentication) {
        return ((JwtAuthenticationToken) authentication).getToken();
    }
}
