package pt.servimatch.platform.audit;

import pt.servimatch.platform.observability.CorrelationIdSupport;

import java.util.UUID;

/**
 * API mínima para que qualquer módulo de domínio registe uma ação sensível
 * em {@code audit_log} (V13, migração já aplicada — ver
 * {@code ARQUITETURA.md} §8.6). A tabela existe desde a V13 sem um único
 * escritor de produção; o primeiro chamador é a decisão administrativa de
 * aprovação de prestador ({@code modules.providers}, onda seguinte), mas a
 * API não tem nenhuma dependência sobre esse caso — qualquer módulo pode
 * chamá-la para qualquer ação sensível futura (moderação, acesso admin,
 * alteração de subscrição).
 *
 * <h2>Transação</h2>
 * {@link #record} participa <b>sempre</b> na transação do chamador — nunca
 * {@code REQUIRES_NEW}. Ver o javadoc de {@link JdbcAuditLogWriter} para o
 * racional completo: um registo de auditoria fora da transação da decisão
 * que descreve é um registo que sobrevive à decisão ter sido revertida, o
 * que é pior do que não ter registo nenhum.
 *
 * <h2>{@code correlation_id}</h2>
 * Nunca é parâmetro deste método. A implementação lê sempre
 * {@link CorrelationIdSupport#currentOrNull()} — o mesmo valor que já
 * aparece nos logs estruturados e nos corpos de erro RFC 9457 do mesmo
 * pedido. Um chamador não pode inventar, omitir nem substituir a
 * correlação; se o quisesse fazer, um pedido malicioso ou um bug de outro
 * módulo poderiam forjar a entrada de auditoria de um pedido diferente.
 *
 * <h2>PII</h2>
 * {@code targetId} identifica o alvo por {@link UUID} — nunca por email,
 * nome ou telefone. {@code metadata} ({@link AuditMetadata}) só aceita
 * valores escalares e não tem forma de receber um objeto de domínio
 * inteiro; ver o javadoc de {@link AuditMetadata} para o que isso cobre e o
 * que continua a ser disciplina do chamador.
 */
public interface AuditLogWriter {

    /**
     * @param actorId    {@code users.id} de quem decidiu, ou {@code null} para ações despoletadas pelo sistema (job, webhook).
     * @param action     identificador estável da ação, ex. {@code "provider.approval.decided"}. Nunca PII.
     * @param targetType identificador estável do tipo de alvo, ex. {@code "provider_profile"}. Nunca PII.
     * @param targetId   UUID do alvo, ou {@code null} se a ação não tiver um alvo único.
     * @param metadata   contexto adicional; {@link AuditMetadata#empty()} se não houver nenhum.
     */
    void record(UUID actorId, String action, String targetType, UUID targetId, AuditMetadata metadata);
}
