package pt.servimatch.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.util.Map;
import java.util.UUID;

import static pt.servimatch.platform.observability.CorrelationIdSupport.currentOrNull;

/**
 * Escritor de {@code audit_log} (V13) por {@link JdbcClient} — coerente com
 * o resto do backend, que não usa JPA.
 *
 * <p><b>Nunca {@code @Transactional}, e muito menos
 * {@code @Transactional(REQUIRES_NEW)}.</b> Esta classe não abre nenhuma
 * transação própria: o {@link JdbcClient} obtém a ligação já associada à
 * transação em curso do chamador (o mesmo mecanismo — {@code DataSourceUtils}
 * por trás do {@code DataSourceTransactionManager} — usado por qualquer
 * outro repositório do projeto), pelo que o {@code INSERT} só fica visível
 * a outras transações se a transação do chamador confirmar (commit).
 *
 * <p>Isto é deliberado, não uma omissão: se o registo de auditoria vivesse
 * numa transação própria ({@code REQUIRES_NEW}), uma decisão de domínio
 * que fosse revertida <em>depois</em> de chamar {@link #record} deixaria
 * uma entrada de auditoria a descrever algo que nunca chegou a acontecer —
 * o inverso exato do que a auditoria promete provar. Participar na mesma
 * transação garante a propriedade que importa: a entrada de auditoria
 * existe se e só se a ação que ela descreve também existiu. Uma auditoria
 * escrita fora da transação da ação é uma auditoria que se perde
 * precisamente quando é mais precisa — quando a ação falha a meio.
 */
@Component
public class JdbcAuditLogWriter implements AuditLogWriter {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcAuditLogWriter(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(UUID actorId, String action, String targetType, UUID targetId, AuditMetadata metadata) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action de auditoria não pode ser vazia");
        }
        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("targetType de auditoria não pode ser vazio");
        }
        Map<String, Object> values = (metadata != null ? metadata : AuditMetadata.empty()).asMap();
        String metadataJson = writeJson(values);

        jdbcClient.sql("""
                        INSERT INTO audit_log (actor_id, action, target_type, target_id, metadata, correlation_id)
                        VALUES (:actorId, :action, :targetType, :targetId, :metadata::jsonb, :correlationId)
                        """)
                .param("actorId", actorId, Types.OTHER)
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId, Types.OTHER)
                .param("metadata", metadataJson)
                .param("correlationId", currentOrNull())
                .update();
    }

    private String writeJson(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            // Não deve acontecer: os únicos valores possíveis em AuditMetadata
            // são String/UUID/boolean/long/Instant, todos serializáveis por
            // omissão. Se acontecer, é um defeito do escritor, não do
            // chamador — falha alto e claro em vez de gravar metadata corrompida.
            throw new IllegalStateException("falha a serializar metadata de auditoria", e);
        }
    }
}
