package pt.servimatch.platform.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pt.servimatch.platform.observability.CorrelationIdSupport;
import pt.servimatch.testsupport.TestDatabase;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova, contra PostGIS real ({@code SharedPostgis}, V13 já aplicada), os
 * dois compromissos não negociáveis do {@link AuditLogWriter}: (1) o
 * {@code correlation_id} vem sempre do MDC, nunca de um parâmetro do
 * chamador; e (2) a escrita participa na transação do chamador — se essa
 * transação reverter, a entrada de auditoria reverte com ela (ver o
 * javadoc de {@link JdbcAuditLogWriter}).
 */
class JdbcAuditLogWriterTest {

    private final DataSource dataSource = TestDatabase.dataSource();
    private final JdbcClient jdbcClient = JdbcClient.create(dataSource);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcAuditLogWriter writer = new JdbcAuditLogWriter(jdbcClient, objectMapper);
    private final TransactionTemplate transactionTemplate =
            new TransactionTemplate(new DataSourceTransactionManager(dataSource));

    @AfterEach
    void clearMdc() {
        MDC.remove(CorrelationIdSupport.MDC_KEY);
    }

    @Test
    void recordsTheActionWithCorrelationIdFromMdcAndScalarMetadataAsJsonb() {
        UUID actorId = createUser();
        UUID targetId = UUID.randomUUID();
        MDC.put(CorrelationIdSupport.MDC_KEY, "corr-caminho-principal");

        writer.record(actorId, "provider.approval.decided", "provider_profile", targetId,
                AuditMetadata.builder().put("from", "PENDING").put("to", "APPROVED").build());

        AuditRow row = jdbcClient.sql("""
                        SELECT actor_id, action, target_type, target_id, correlation_id, metadata::text AS metadata_text
                        FROM audit_log WHERE target_id = :targetId
                        """)
                .param("targetId", targetId, Types.OTHER)
                .query((rs, rowNum) -> new AuditRow(
                        (UUID) rs.getObject("actor_id"),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        (UUID) rs.getObject("target_id"),
                        rs.getString("correlation_id"),
                        rs.getString("metadata_text")))
                .single();

        assertThat(row.actorId()).isEqualTo(actorId);
        assertThat(row.action()).isEqualTo("provider.approval.decided");
        assertThat(row.targetType()).isEqualTo("provider_profile");
        assertThat(row.targetId()).isEqualTo(targetId);
        assertThat(row.correlationId()).isEqualTo("corr-caminho-principal");
        // Comparado por valor, não por substring: o jsonb do Postgres reserializa
        // o texto (espaço depois de ':', possível reordenação de chaves), pelo
        // que uma comparação de substring literal é frágil por construção.
        Map<String, Object> metadata = readMetadata(row.metadataText());
        assertThat(metadata).containsEntry("from", "PENDING").containsEntry("to", "APPROVED");
    }

    private record AuditRow(UUID actorId, String action, String targetType, UUID targetId, String correlationId,
                             String metadataText) {
    }

    @Test
    void nullActorIdIsAcceptedForSystemTriggeredActions() {
        UUID targetId = UUID.randomUUID();

        writer.record(null, "reconciliation.completed", "payment_gateway_event", targetId, AuditMetadata.empty());

        long count = jdbcClient.sql("SELECT count(*) FROM audit_log WHERE target_id = :targetId")
                .param("targetId", targetId, Types.OTHER)
                .query(Long.class)
                .single();
        assertThat(count).as("a linha tem de ter sido escrita, apesar de actor_id nulo").isEqualTo(1L);

        // JdbcClient.query(UUID.class).single() exige valor não nulo; usa-se
        // optional() porque sabemos, pela contagem acima, que existe exatamente
        // uma linha — um Optional vazio aqui só pode significar actor_id NULL.
        Optional<UUID> actorId = jdbcClient.sql("SELECT actor_id FROM audit_log WHERE target_id = :targetId")
                .param("targetId", targetId, Types.OTHER)
                .query(UUID.class)
                .optional();
        assertThat(actorId).isEmpty();
    }

    @Test
    void rejectsBlankActionBeforeTouchingTheDatabase() {
        UUID targetId = UUID.randomUUID();

        assertThatThrownBy(() -> writer.record(null, "  ", "provider_profile", targetId, AuditMetadata.empty()))
                .isInstanceOf(IllegalArgumentException.class);

        long count = jdbcClient.sql("SELECT count(*) FROM audit_log WHERE target_id = :targetId")
                .param("targetId", targetId, Types.OTHER)
                .query(Long.class)
                .single();
        assertThat(count).isZero();
    }

    @Test
    void participatesInTheCallersTransactionSoARollbackDiscardsTheAuditEntryToo() {
        UUID actorId = createUser();
        UUID targetId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            writer.record(actorId, "provider.approval.decided", "provider_profile", targetId, AuditMetadata.empty());
            throw new IllegalStateException("simula falha da decisão de domínio depois de auditar");
        })).isInstanceOf(IllegalStateException.class);

        long count = jdbcClient.sql("SELECT count(*) FROM audit_log WHERE target_id = :targetId")
                .param("targetId", targetId, Types.OTHER)
                .query(Long.class)
                .single();
        assertThat(count)
                .as("a entrada de auditoria não pode sobreviver à reversão da transação que descreve")
                .isZero();
    }

    private UUID createUser() {
        UUID userId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO users (id, keycloak_sub, email, display_name)
                        VALUES (:id, :sub, :email, 'Test Actor')
                        """)
                .param("id", userId)
                .param("sub", "kc-" + userId)
                .param("email", "actor-" + userId + "@example.test")
                .update();
        return userId;
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("metadata de auditoria devolvida pelo Postgres não é JSON válido", e);
        }
    }
}
