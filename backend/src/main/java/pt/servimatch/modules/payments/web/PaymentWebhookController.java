package pt.servimatch.modules.payments.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.payments.internal.IngestOutcome;
import pt.servimatch.modules.payments.internal.WebhookIngestService;
import pt.servimatch.platform.error.ProblemDetailsSupport;
import pt.servimatch.platform.error.ProblemType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code POST /v1/webhooks/payments/{gateway}} — endpoint público
 * (contrato {@code security: []}, autenticado pela assinatura do gateway,
 * já permitido em {@code SecurityConfig}). Superfície exposta: tamanho do
 * corpo limitado, rejeição cedo, sem detalhe de erro que ajude a sondar o
 * formato esperado (skill {@code payment-webhook-hardening}). Rate
 * limiting global já cobre este caminho ({@code RateLimitFilter}, aplicado
 * antes da autenticação a todos os pedidos).
 */
@RestController
public class PaymentWebhookController {

    /** Limite defensivo do corpo do webhook — payloads reais destes gateways nunca chegam perto disto. */
    private static final int MAX_BODY_BYTES = 256 * 1024;

    private final WebhookIngestService webhookIngestService;

    public PaymentWebhookController(WebhookIngestService webhookIngestService) {
        this.webhookIngestService = webhookIngestService;
    }

    @PostMapping("/v1/webhooks/payments/{gateway}")
    public ResponseEntity<Object> receive(@PathVariable String gateway, HttpServletRequest request) throws IOException {
        byte[] body;
        try {
            body = readBounded(request.getInputStream(), MAX_BODY_BYTES);
        } catch (BodyTooLargeException e) {
            return problem(HttpStatus.BAD_REQUEST, ProblemType.VALIDATION, "Pedido inválido", "Corpo do pedido excede o limite permitido.");
        }

        Map<String, String> headers = extractHeaders(request);
        IngestOutcome outcome = webhookIngestService.ingest(gateway, body, headers);

        return switch (outcome.result()) {
            case UNKNOWN_GATEWAY -> problem(HttpStatus.BAD_REQUEST, ProblemType.VALIDATION, "Pedido inválido", "Gateway não suportado.");
            case BAD_REQUEST -> problem(HttpStatus.BAD_REQUEST, ProblemType.VALIDATION, "Pedido inválido", "Corpo do pedido malformado.");
            case UNAUTHORIZED -> problem(HttpStatus.UNAUTHORIZED, ProblemType.UNAUTHENTICATED, "Assinatura inválida", "A assinatura do pedido não pôde ser verificada.");
            case DUPLICATE, PROCESSED -> ResponseEntity.noContent().build();
        };
    }

    private ResponseEntity<Object> problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetailsSupport.of(status, type, title, detail);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problemDetail);
    }

    private static byte[] readBounded(InputStream inputStream, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] chunk = new byte[8192];
        int total = 0;
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new BodyTooLargeException();
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Collections.emptyMap();
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    private static final class BodyTooLargeException extends RuntimeException {
    }
}
