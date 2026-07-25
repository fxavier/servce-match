package pt.servimatch.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import pt.servimatch.platform.error.ProblemDetailsResponseWriter;
import pt.servimatch.platform.error.ProblemType;

import java.io.IOException;

/**
 * Produz um 401 em formato RFC 9457 quando o pedido não tem (ou tem
 * inválido) um {@code Authorization: Bearer}. Substitui a página HTML por
 * omissão do Spring Security — este é um backend, todos os clientes são
 * API (web BFF, mobile).
 */
public class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemDetailsAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ProblemDetailsResponseWriter.write(
                response,
                objectMapper,
                HttpStatus.UNAUTHORIZED,
                ProblemType.UNAUTHENTICATED,
                "Não autenticado",
                "É necessário um token de acesso válido (Authorization: Bearer <token>).");
    }
}
