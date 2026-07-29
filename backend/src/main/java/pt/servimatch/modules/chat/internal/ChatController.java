package pt.servimatch.modules.chat.internal;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.chat.internal.web.CreateMessageRequest;
import pt.servimatch.modules.chat.internal.web.MessageDto;
import pt.servimatch.modules.chat.internal.web.MessagePageDto;
import pt.servimatch.modules.users.UsersApi;

import java.util.UUID;

/**
 * Controlador REST de {@code chat} — ver {@code docs/api/openapi.yaml} (tag
 * Chat). Autorização por participante e <em>gating</em> de subscrição vivem
 * em {@link ChatService}, não aqui — este controlador só resolve o
 * utilizador autenticado e delega.
 *
 * <p>{@code @Lazy}: ver nota em
 * {@code pt.servimatch.modules.requests.internal.RequestsController}.
 */
@RestController
@Lazy
class ChatController {

    private final ChatService chatService;
    private final UsersApi usersApi;

    ChatController(ChatService chatService, UsersApi usersApi) {
        this.chatService = chatService;
        this.usersApi = usersApi;
    }

    @GetMapping("/v1/conversations/{conversationId}/messages")
    MessagePageDto listMessages(Authentication authentication, @PathVariable UUID conversationId,
                                 @RequestParam(required = false) String cursor,
                                 @RequestParam(required = false, defaultValue = "20") int limit) {
        UUID viewerId = usersApi.ensureProvisioned(jwt(authentication));
        return chatService.listMessages(conversationId, viewerId, cursor, Math.min(Math.max(limit, 1), 100));
    }

    @PostMapping("/v1/conversations/{conversationId}/messages")
    ResponseEntity<MessageDto> sendMessage(Authentication authentication, @PathVariable UUID conversationId,
                                            @Valid @RequestBody CreateMessageRequest request) {
        UUID senderId = usersApi.ensureProvisioned(jwt(authentication));
        MessageDto created = chatService.sendMessage(conversationId, senderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private static Jwt jwt(Authentication authentication) {
        return ((JwtAuthenticationToken) authentication).getToken();
    }
}
