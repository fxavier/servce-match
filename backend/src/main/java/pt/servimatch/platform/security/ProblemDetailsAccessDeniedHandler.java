package pt.servimatch.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import pt.servimatch.platform.error.ProblemDetailsResponseWriter;
import pt.servimatch.platform.error.ProblemType;

import java.io.IOException;

/**
 * Produz um 403 em formato RFC 9457 quando o principal está autenticado mas
 * não tem a role exigida pelo endpoint. Verificação de <em>ownership</em>
 * (o recurso pertence ao principal) é responsabilidade de cada módulo de
 * domínio e passa por {@link org.springframework.security.access.AccessDeniedException}
 * lançada no serviço, apanhada aqui do mesmo modo.
 */
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailsAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        ProblemDetailsResponseWriter.write(
                response,
                objectMapper,
                HttpStatus.FORBIDDEN,
                ProblemType.FORBIDDEN,
                "Acesso negado",
                "Não tem permissão para efetuar esta operação.");
    }
}
