package pt.servimatch.modules.uploads.internal;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.uploads.internal.web.CreateUploadRequest;
import pt.servimatch.modules.uploads.internal.web.UploadTargetResponse;
import pt.servimatch.modules.users.UsersApi;

import java.util.UUID;

/**
 * {@code POST /v1/uploads} (operationId {@code createUpload}). Sem
 * {@code @PreAuthorize}: qualquer principal autenticado pode pedir um URL de
 * upload (o contrato não restringe por role — {@code security: []} não está
 * definido para esta operação, logo aplica-se {@code oidc: []} do nível de
 * topo, "autenticado" chega); a fronteira real é o {@code ownerUserId}
 * (dono = quem pediu), aplicada em {@link UploadsService}.
 *
 * <p>{@code @Lazy}: ver nota em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@RestController
@Lazy
class UploadsController {

    private final UploadsService uploadsService;
    private final UsersApi usersApi;

    UploadsController(UploadsService uploadsService, UsersApi usersApi) {
        this.uploadsService = uploadsService;
        this.usersApi = usersApi;
    }

    @PostMapping("/v1/uploads")
    ResponseEntity<UploadTargetResponse> createUpload(Authentication authentication, @Valid @RequestBody CreateUploadRequest request) {
        UUID ownerId = usersApi.ensureProvisioned(jwt(authentication));
        UploadsService.UploadTarget target = uploadsService.createUploadTarget(
                ownerId, request.purpose(), request.contentType(), request.contentLength());
        UploadTargetResponse response = new UploadTargetResponse(
                target.imageId(), target.uploadUrl(), target.headers(), target.expiresAt(), target.maxSizeBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private static Jwt jwt(Authentication authentication) {
        return ((JwtAuthenticationToken) authentication).getToken();
    }
}
