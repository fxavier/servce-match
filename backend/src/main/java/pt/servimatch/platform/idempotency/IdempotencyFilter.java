package pt.servimatch.platform.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import pt.servimatch.platform.error.ProblemDetailsResponseWriter;
import pt.servimatch.platform.error.ProblemType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/**
 * Honra o cabeçalho {@code Idempotency-Key} (ver
 * {@code docs/api/openapi.yaml#/components/parameters/IdempotencyKey}) em
 * escritas não idempotentes ({@code POST}, {@code PATCH}):
 *
 * <ul>
 *   <li>Sem o cabeçalho: comportamento normal, sem qualquer efeito.</li>
 *   <li>Primeira vez que se vê a chave (por principal + rota + método): a
 *       resposta é executada normalmente e depois guardada.</li>
 *   <li>Repetição com o mesmo corpo: devolve a resposta guardada, sem
 *       invocar o módulo de domínio outra vez.</li>
 *   <li>Repetição com corpo diferente: {@code 409} — a chave já foi usada
 *       para outro pedido.</li>
 * </ul>
 *
 * <p>Registado <b>depois</b> da autenticação na cadeia de segurança (ver
 * {@code pt.servimatch.config.SecurityConfig}), para poder isolar a chave
 * por utilizador autenticado e não apenas por IP.
 *
 * <p><b>Pedidos sem principal autenticado (incluindo anónimo) não passam por
 * este filtro.</b> Um balde partilhado por "anonymous" cruzaria pedidos de
 * clientes não relacionados: um atacante sem token podia semear uma chave
 * conhecida com uma resposta de erro (ex. {@code 401}) e, se um chamador
 * legítimo viesse depois a repetir a mesma chave — caso de
 * {@code POST /v1/webhooks/payments/**}, público por necessidade — receberia
 * um {@code 409 idempotency-key-conflict} em vez de ser processado,
 * silenciando um evento nunca persistido. A idempotência de escrita para
 * pedidos não autenticados, quando necessária, é responsabilidade do próprio
 * endpoint (ex. a restrição {@code UNIQUE (gateway, raw_event_id)} do
 * webhook de pagamentos), não deste filtro.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Set<String> ELIGIBLE_METHODS = Set.of("POST", "PATCH");

    private final IdempotencyStore store;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyStore store, IdempotencyProperties properties, ObjectMapper objectMapper) {
        this.store = store;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String idempotencyKey = request.getHeader(properties.headerName());
        Optional<String> principal = authenticatedPrincipalName();
        if (!ELIGIBLE_METHODS.contains(request.getMethod())
                || idempotencyKey == null
                || idempotencyKey.isBlank()
                || principal.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] bodyBytes = StreamUtils.copyToByteArray(request.getInputStream());
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request, bodyBytes);
        String requestHash = sha256Hex(bodyBytes);
        String scopeKey = buildScopeKey(request, principal.get(), idempotencyKey);

        Optional<CachedIdempotentResponse> existing = store.find(scopeKey);
        if (existing.isPresent()) {
            CachedIdempotentResponse cached = existing.get();
            if (!cached.requestBodyHash().equals(requestHash)) {
                ProblemDetailsResponseWriter.write(
                        response,
                        objectMapper,
                        HttpStatus.CONFLICT,
                        ProblemType.IDEMPOTENCY_KEY_CONFLICT,
                        "Idempotency-Key reutilizada",
                        "Esta Idempotency-Key já foi usada com um pedido de conteúdo diferente.");
                return;
            }
            replay(cached, response);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(wrappedRequest, wrappedResponse);

        int status = wrappedResponse.getStatus();
        if (status < 500) {
            store.save(scopeKey, new CachedIdempotentResponse(
                    status,
                    wrappedResponse.getContentType(),
                    wrappedResponse.getContentAsByteArray(),
                    requestHash));
        }
        wrappedResponse.copyBodyToResponse();
    }

    private void replay(CachedIdempotentResponse cached, HttpServletResponse response) throws IOException {
        response.setStatus(cached.status());
        if (cached.contentType() != null) {
            response.setContentType(cached.contentType());
        } else {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        }
        response.getOutputStream().write(cached.body());
    }

    private String buildScopeKey(HttpServletRequest request, String principal, String idempotencyKey) {
        return principal + '|' + request.getMethod() + '|' + request.getRequestURI() + '|' + idempotencyKey;
    }

    /**
     * Nome do principal autenticado, ou vazio se o pedido não tiver
     * autenticação real (sem token, ou {@link AnonymousAuthenticationToken}
     * — este último é o que o Spring Security atribui por omissão a um
     * pedido sem credenciais, incluindo os endpoints {@code permitAll()}
     * como o webhook de pagamentos). Nunca se cria uma chave partilhada por
     * "anonymous": ver javadoc da classe.
     */
    private Optional<String> authenticatedPrincipalName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null) {
            return Optional.empty();
        }
        return Optional.of(authentication.getName());
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JDK; this is unreachable in practice.
            return HexFormat.of().formatHex(String.valueOf(bytes.length).getBytes(StandardCharsets.UTF_8));
        }
    }
}
