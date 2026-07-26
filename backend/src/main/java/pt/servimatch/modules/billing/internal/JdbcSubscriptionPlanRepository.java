package pt.servimatch.modules.billing.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.servimatch.modules.billing.SubscriptionPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSubscriptionPlanRepository {

    private final JdbcClient jdbcClient;

    public JdbcSubscriptionPlanRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<SubscriptionPlan> findActive() {
        return jdbcClient.sql("""
                        SELECT id, code, name, price_amount_cents, price_currency, billing_interval,
                               max_categories, max_areas, ranking_boost, has_badge, active
                        FROM subscription_plan
                        WHERE active = true
                        ORDER BY price_amount_cents ASC, code ASC
                        """)
                .query(JdbcSubscriptionPlanRepository::mapRow)
                .list();
    }

    public Optional<SubscriptionPlan> findById(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, code, name, price_amount_cents, price_currency, billing_interval,
                               max_categories, max_areas, ranking_boost, has_badge, active
                        FROM subscription_plan
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(JdbcSubscriptionPlanRepository::mapRow)
                .optional();
    }

    private static SubscriptionPlan mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SubscriptionPlan(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getLong("price_amount_cents"),
                rs.getString("price_currency"),
                rs.getString("billing_interval"),
                (Integer) rs.getObject("max_categories"),
                (Integer) rs.getObject("max_areas"),
                rs.getInt("ranking_boost"),
                rs.getBoolean("has_badge"),
                rs.getBoolean("active"));
    }
}
