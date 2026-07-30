package pt.servimatch.modules.reviews.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

/**
 * Acesso a {@code review} (V10). A pré-condição "só avalia quem teve
 * {@code Booking COMPLETED}" e a unicidade {@code (booking_id, author_id)}
 * são impostas pelo próprio schema (trigger + índice único, ver a migração)
 * — a camada de aplicação valida antes por mensagens de erro melhores, mas
 * a garantia final vive na base de dados.
 *
 * <p>{@code @Lazy}: ver nota equivalente em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@Repository
@Lazy
class ReviewRepository {

    private final JdbcClient jdbcClient;

    ReviewRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    ReviewRow insert(UUID bookingId, UUID authorId, UUID targetId, int rating, String comment) {
        return jdbcClient.sql("""
                        INSERT INTO review (booking_id, author_id, target_id, rating, comment)
                        VALUES (:bookingId, :authorId, :targetId, :rating, :comment)
                        RETURNING id, booking_id, author_id, target_id, rating, comment, created_at
                        """)
                .param("bookingId", bookingId)
                .param("authorId", authorId)
                .param("targetId", targetId)
                .param("rating", rating)
                .param("comment", comment, Types.VARCHAR)
                .query(this::mapRow)
                .single();
    }

    /**
     * Página de {@code listProviderReviews} — {@code target_id = :targetUserId}
     * autoriza no próprio {@code WHERE} o único filtro que este endpoint
     * público precisa (não há participante a verificar: é uma leitura
     * pública). Ordenada por {@code created_at DESC, id DESC}, servida por
     * {@code idx_review_target_id_created_at_id} (V17) — igualdade e
     * ordenação na mesma pesquisa por índice, sem sort em memória.
     */
    List<ReviewWithAuthorRow> findPageByTarget(UUID targetUserId, CursorCodec.Position after, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, author_id, target_id, rating, comment, created_at, provider_response
                FROM review WHERE target_id = :targetUserId
                """);
        if (after != null) {
            sql.append(" AND (created_at, id) < (:afterCreatedAt, :afterId) ");
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit ");
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString())
                .param("targetUserId", targetUserId)
                .param("limit", limit);
        if (after != null) {
            spec = spec.param("afterCreatedAt", Timestamp.from(after.createdAt()))
                    .param("afterId", after.id());
        }
        return spec.query(this::mapRowWithAuthor).list();
    }

    private ReviewWithAuthorRow mapRowWithAuthor(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewWithAuthorRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("author_id"),
                (UUID) rs.getObject("target_id"),
                rs.getInt("rating"),
                rs.getString("comment"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("provider_response"));
    }

    private ReviewRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("booking_id"),
                (UUID) rs.getObject("author_id"),
                (UUID) rs.getObject("target_id"),
                rs.getInt("rating"),
                rs.getString("comment"),
                rs.getTimestamp("created_at").toInstant());
    }
}
