package pt.servimatch.modules.providers.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Acesso a {@code provider_profile} (V4). {@code company} não é lido aqui —
 * ver nota em {@link #findById}.
 *
 * <p>{@code @Lazy}: ver nota equivalente em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@Repository
@Lazy
class ProviderRepository {

    private final JdbcClient jdbcClient;

    ProviderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Optional<ProviderProfileRow> findByUserId(UUID userId) {
        return jdbcClient.sql(SELECT_BASE + " WHERE p.user_id = :userId")
                .param("userId", userId)
                .query(this::mapRow)
                .optional();
    }

    Optional<ProviderProfileRow> findById(UUID providerId) {
        return jdbcClient.sql(SELECT_BASE + " WHERE p.id = :id")
                .param("id", providerId)
                .query(this::mapRow)
                .optional();
    }

    Set<UUID> findWorkedCategoryIds(UUID providerId) {
        return Set.copyOf(jdbcClient.sql("SELECT category_id FROM provider_category WHERE provider_id = :providerId")
                .param("providerId", providerId)
                .query((rs, rowNum) -> (UUID) rs.getObject("category_id"))
                .list());
    }

    Optional<UUID> insertIfAbsent(UUID userId) {
        return jdbcClient.sql("""
                        INSERT INTO provider_profile (user_id)
                        VALUES (:userId)
                        ON CONFLICT (user_id) DO NOTHING
                        RETURNING id
                        """)
                .param("userId", userId)
                .query(UUID.class)
                .optional();
    }

    private static final String SELECT_BASE = """
            SELECT p.id, p.user_id, p.headline, c.name AS company_name, p.verified,
                   p.approval_status, p.visibility_state, p.rating_avg, p.rating_count
            FROM provider_profile p
            LEFT JOIN company c ON c.id = p.company_id
            """;

    private ProviderProfileRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal ratingAvg = rs.getBigDecimal("rating_avg");
        return new ProviderProfileRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("user_id"),
                rs.getString("headline"),
                rs.getString("company_name"),
                rs.getBoolean("verified"),
                rs.getString("approval_status"),
                rs.getString("visibility_state"),
                ratingAvg == null ? BigDecimal.ZERO : ratingAvg,
                rs.getInt("rating_count"));
    }
}
