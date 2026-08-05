package pt.servimatch.modules.requests;

/**
 * Espelha {@code components.schemas.UrgencyLevel} ({@code openapi.yaml:997-999})
 * e o {@code CHECK (urgency IN (...))} de {@code service_request.urgency}
 * (V7). Enum Java, não {@code String} com {@code @Pattern} — mesmo padrão de
 * {@link pt.servimatch.modules.providers.ProviderApprovalDecision} (ver o seu
 * javadoc em {@code UpdateProviderApprovalRequest}): um valor fora do enum
 * falha a desserialização Jackson e o {@code GlobalExceptionHandler} devolve
 * {@code 400} antes de chegar a qualquer validação de negócio, e muito antes
 * do {@code INSERT}.
 *
 * <p>Achado M5 da auditoria de segurança (Onda C): antes desta correção,
 * {@code CreateServiceRequestRequest.urgency} era {@code String} sem
 * {@code @Pattern} nem enum — {@code "FLEXIBLE"} atravessava o bean
 * validation, chegava ao {@code INSERT} e só era travado pelo {@code CHECK}
 * da base de dados, que o backend traduzia em {@code 409 "Conflito de
 * estado"} em vez de {@code 400}. O handler dessa exceção de violação de
 * integridade também escrevia a linha inteira (morada incluída) no log —
 * achado A1, corrigido em paralelo por {@code backend-platform}.
 */
public enum UrgencyLevel {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
