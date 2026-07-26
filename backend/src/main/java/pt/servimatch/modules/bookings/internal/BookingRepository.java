package pt.servimatch.modules.bookings.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code booking} (V10).
 *
 * <p>{@code @Lazy}: ver nota equivalente em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@Repository
@Lazy
class BookingRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, proposal_id, scheduled_start, scheduled_end, status, completed_at, created_at
            FROM booking
            """;

    private final JdbcClient jdbcClient;

    BookingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Idempotente por desenho: {@code uq_booking_proposal_id} (V10) garante no
     * máximo uma marcação por proposta. Necessário porque é chamado a partir
     * de um {@code @ApplicationModuleListener} (entrega <em>at-least-once</em>,
     * skill {@code spring-modulith-module}).
     */
    UUID insertIfAbsent(UUID proposalId) {
        return jdbcClient.sql("""
                        INSERT INTO booking (proposal_id) VALUES (:proposalId)
                        ON CONFLICT (proposal_id) DO NOTHING
                        RETURNING id
                        """)
                .param("proposalId", proposalId)
                .query(UUID.class)
                .optional()
                .orElseGet(() -> findByProposalId(proposalId).orElseThrow().id());
    }

    Optional<BookingRow> findById(UUID id) {
        return jdbcClient.sql(SELECT_COLUMNS + " WHERE id = :id")
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    Optional<BookingRow> findByProposalId(UUID proposalId) {
        return jdbcClient.sql(SELECT_COLUMNS + " WHERE proposal_id = :proposalId")
                .param("proposalId", proposalId)
                .query(this::mapRow)
                .optional();
    }

    /**
     * CAS {@code CONFIRMED|IN_PROGRESS → COMPLETED}. @return true se a
     * transição ocorreu nesta chamada.
     */
    boolean complete(UUID id) {
        int rows = jdbcClient.sql("""
                        UPDATE booking SET status = 'COMPLETED', completed_at = now()
                        WHERE id = :id AND status IN ('CONFIRMED', 'IN_PROGRESS')
                        """)
                .param("id", id)
                .update();
        return rows > 0;
    }

    private BookingRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Timestamp start = rs.getTimestamp("scheduled_start");
        java.sql.Timestamp end = rs.getTimestamp("scheduled_end");
        java.sql.Timestamp completedAt = rs.getTimestamp("completed_at");
        return new BookingRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("proposal_id"),
                start == null ? null : start.toInstant(),
                end == null ? null : end.toInstant(),
                rs.getString("status"),
                completedAt == null ? null : completedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
