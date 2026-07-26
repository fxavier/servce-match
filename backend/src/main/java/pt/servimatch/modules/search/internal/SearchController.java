package pt.servimatch.modules.search.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.search.internal.dto.ProviderSummaryPage;
import pt.servimatch.platform.error.ProblemDetailsSupport;
import pt.servimatch.platform.error.ProblemType;

/**
 * {@code GET /v1/search/providers} (operationId {@code searchProviders}) —
 * público (`security: []` no contrato), ver {@code SecurityConfig}
 * ({@code PUBLIC_GET_ENDPOINTS}, já inclui esta rota — não é preciso
 * alterar segurança).
 *
 * <p>Todos os parâmetros chegam como {@code String} deliberadamente (em vez
 * de tipos como {@code UUID}/{@code Double} diretamente no
 * {@code @RequestParam}): assim o erro de conversão nunca escapa como
 * {@code MethodArgumentTypeMismatchException} do Spring (que não produz
 * RFC 9457 — {@code GlobalExceptionHandler}, que não é deste módulo, não o
 * trata), e cai sempre em {@link InvalidSearchParametersException},
 * mapeada aqui mesmo.
 */
@RestController
class SearchController {

    private final SearchProvidersService service;

    SearchController(SearchProvidersService service) {
        this.service = service;
    }

    @GetMapping("/v1/search/providers")
    ProviderSummaryPage searchProviders(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String lat,
            @RequestParam(required = false) String lon,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String cursor) {
        return service.search(categoryId, lat, lon, regionCode, q, limit, cursor);
    }

    @ExceptionHandler(InvalidSearchParametersException.class)
    ResponseEntity<ProblemDetail> handleInvalidParameters(InvalidSearchParametersException ex) {
        ProblemDetail problemDetail = ProblemDetailsSupport.of(
                HttpStatus.BAD_REQUEST,
                ProblemType.VALIDATION,
                "Parâmetros de pesquisa inválidos",
                ex.getMessage());
        problemDetail.setProperty("errors", java.util.List.of(java.util.Map.of(
                "field", ex.field(),
                "message", ex.getMessage())));
        return ResponseEntity.badRequest().body(problemDetail);
    }
}
