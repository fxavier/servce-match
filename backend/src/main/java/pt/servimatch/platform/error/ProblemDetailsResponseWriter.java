package pt.servimatch.platform.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

/**
 * Escreve um {@link ProblemDetail} diretamente numa {@link HttpServletResponse},
 * para os pontos que respondem a partir da cadeia de filtros do Servlet
 * (autenticação, autorização, rate limiting, idempotência) e por isso não
 * passam pelo {@code @RestControllerAdvice}.
 */
public final class ProblemDetailsResponseWriter {

    private ProblemDetailsResponseWriter() {
    }

    public static void write(HttpServletResponse response,
                              ObjectMapper objectMapper,
                              HttpStatus status,
                              String type,
                              String title,
                              String detail) throws IOException {
        ProblemDetail problemDetail = ProblemDetailsSupport.of(status, type, title, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
