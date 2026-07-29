package pt.servimatch.modules.categories.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code category} (V3, semeada por V15).
 *
 * <p>{@code @Lazy}: ver nota equivalente em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@Repository
@Lazy
class CategoryRepository {

    private static final String SELECT_COLUMNS = "SELECT id, parent_id, slug, name, active FROM category";

    private final JdbcClient jdbcClient;

    CategoryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Optional<CategoryRow> findById(UUID id) {
        return jdbcClient.sql(SELECT_COLUMNS + " WHERE id = :id")
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    Optional<CategoryRow> findActiveById(UUID id) {
        return jdbcClient.sql(SELECT_COLUMNS + " WHERE id = :id AND active = true")
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    /** {@code GET /v1/categories}: só ativas, opcionalmente filtradas por {@code parentId}, ordenadas por nome. */
    List<CategoryRow> findActive(UUID parentId) {
        String sql = SELECT_COLUMNS + " WHERE active = true"
                + (parentId != null ? " AND parent_id = :parentId" : " AND parent_id IS NULL")
                + " ORDER BY name";
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql);
        if (parentId != null) {
            spec = spec.param("parentId", parentId);
        }
        return spec.query(this::mapRow).list();
    }

    private CategoryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new CategoryRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("parent_id"),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getBoolean("active"));
    }
}
