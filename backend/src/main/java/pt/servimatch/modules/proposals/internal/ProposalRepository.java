package pt.servimatch.modules.proposals.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code proposal} (V8).
 *
 * <p>{@code @Lazy}: ver nota equivalente em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@Repository
@Lazy
class ProposalRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, request_id, provider_id, price_amount_cents, price_currency,
                   description, lead_time_days, valid_until, status, created_at
            FROM proposal
            """;

    private final JdbcClient jdbcClient;

    ProposalRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * "Reenviar substitui a anterior" (ARQUITETURA §4.4): cancela qualquer
     * proposta {@code SENT} do mesmo prestador para o mesmo pedido antes de
     * inserir a nova — evita colidir com o índice único parcial
     * {@code uq_proposal_request_provider_active}.
     */
    int cancelActiveForResend(UUID requestId, UUID providerId) {
        return jdbcClient.sql("""
                        UPDATE proposal SET status = 'CANCELLED'
                        WHERE request_id = :requestId AND provider_id = :providerId AND status = 'SENT'
                        """)
                .param("requestId", requestId)
                .param("providerId", providerId)
                .update();
    }

    UUID insert(UUID requestId, UUID providerId, long priceAmountCents, String currency,
                String description, Integer leadTimeDays, java.time.Instant validUntil) {
        // java.sql.Timestamp em vez de Instant diretamente: nem todas as versões do
        // driver JDBC do PostgreSQL suportam bind de java.time.Instant por setObject;
        // Timestamp é universalmente suportado (mesmo padrão de CursorCodec/findPage).
        java.sql.Timestamp validUntilTs = validUntil == null ? null : java.sql.Timestamp.from(validUntil);
        return jdbcClient.sql("""
                        INSERT INTO proposal (request_id, provider_id, price_amount_cents, price_currency,
                                               description, lead_time_days, valid_until)
                        VALUES (:requestId, :providerId, :priceAmountCents, :currency, :description, :leadTimeDays, :validUntil)
                        RETURNING id
                        """)
                .param("requestId", requestId)
                .param("providerId", providerId)
                .param("priceAmountCents", priceAmountCents)
                .param("currency", currency)
                .param("description", description)
                .param("leadTimeDays", leadTimeDays, Types.INTEGER)
                .param("validUntil", validUntilTs, Types.TIMESTAMP)
                .query(UUID.class)
                .single();
    }

    Optional<ProposalRow> findById(UUID id) {
        return jdbcClient.sql(SELECT_COLUMNS + " WHERE id = :id")
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    List<ProposalRow> findPage(UUID requestId, java.time.Instant afterCreatedAt, UUID afterId, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(" WHERE request_id = :requestId ");
        if (afterCreatedAt != null) {
            sql.append(" AND (created_at, id) < (:afterCreatedAt, :afterId) ");
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit ");
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString())
                .param("requestId", requestId)
                .param("limit", limit);
        if (afterCreatedAt != null) {
            spec = spec.param("afterCreatedAt", java.sql.Timestamp.from(afterCreatedAt)).param("afterId", afterId);
        }
        return spec.query(this::mapRow).list();
    }

    /** CAS {@code SENT → ACCEPTED}. @return true se a transição ocorreu nesta chamada. */
    boolean accept(UUID proposalId) {
        int rows = jdbcClient.sql("UPDATE proposal SET status = 'ACCEPTED' WHERE id = :id AND status = 'SENT'")
                .param("id", proposalId)
                .update();
        return rows > 0;
    }

    void supersedeOthers(UUID requestId, UUID acceptedProposalId) {
        jdbcClient.sql("""
                        UPDATE proposal SET status = 'SUPERSEDED'
                        WHERE request_id = :requestId AND id <> :acceptedId AND status = 'SENT'
                        """)
                .param("requestId", requestId)
                .param("acceptedId", acceptedProposalId)
                .update();
    }

    private ProposalRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Timestamp validUntil = rs.getTimestamp("valid_until");
        Object leadTime = rs.getObject("lead_time_days");
        return new ProposalRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("request_id"),
                (UUID) rs.getObject("provider_id"),
                rs.getLong("price_amount_cents"),
                rs.getString("price_currency"),
                rs.getString("description"),
                leadTime == null ? null : ((Number) leadTime).intValue(),
                validUntil == null ? null : validUntil.toInstant(),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant());
    }
}
