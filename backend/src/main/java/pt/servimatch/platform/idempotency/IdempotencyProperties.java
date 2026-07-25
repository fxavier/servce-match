package pt.servimatch.platform.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração da infraestrutura de idempotência (cabeçalho
 * {@code Idempotency-Key} em escritas não idempotentes, ver
 * {@code docs/api/openapi.yaml} § {@code IdempotencyKey}).
 *
 * @param headerName nome do cabeçalho aceite (por omissão {@code Idempotency-Key}).
 * @param retention  janela de retenção da resposta armazenada; passado este
 *                   período a mesma chave pode ser reutilizada como nova.
 * @param backend    {@code in-memory} (omissão, single-instance) ou
 *                   {@code redis} (obrigatório multi-instância, ADR-0006).
 */
@ConfigurationProperties(prefix = "servimatch.idempotency")
public record IdempotencyProperties(
        String headerName,
        Duration retention,
        String backend
) {
    public IdempotencyProperties {
        if (headerName == null || headerName.isBlank()) {
            headerName = "Idempotency-Key";
        }
        if (retention == null) {
            retention = Duration.ofHours(24);
        }
        if (backend == null || backend.isBlank()) {
            backend = "in-memory";
        }
    }
}
