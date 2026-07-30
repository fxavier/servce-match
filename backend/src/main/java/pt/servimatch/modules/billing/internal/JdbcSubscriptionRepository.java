package pt.servimatch.modules.billing.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.servimatch.modules.billing.Subscription;
import pt.servimatch.modules.billing.SubscriptionStatus;
import pt.servimatch.modules.billing.SubscriptionVisibilitySql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSubscriptionRepository {

    private final JdbcClient jdbcClient;

    public JdbcSubscriptionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Subscription insertPending(UUID providerId, UUID planId, String gateway) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO subscription (id, provider_id, plan_id, status, gateway)
                        VALUES (:id, :providerId, :planId, 'PENDING', :gateway)
                        """)
                .param("id", id)
                .param("providerId", providerId)
                .param("planId", planId)
                .param("gateway", gateway)
                .update();
        return findById(id).orElseThrow();
    }

    public Optional<Subscription> findById(UUID id) {
        return jdbcClient.sql(SELECT + " WHERE id = :id")
                .param("id", id)
                .query(JdbcSubscriptionRepository::mapRow)
                .optional();
    }

    /** Subscrição em estado não-terminal do prestador (no máximo uma, por constraint). */
    public Optional<Subscription> findNonTerminalByProvider(UUID providerId) {
        return jdbcClient.sql(SELECT + " WHERE provider_id = :providerId AND status IN ('PENDING','ACTIVE','PAST_DUE')")
                .param("providerId", providerId)
                .query(JdbcSubscriptionRepository::mapRow)
                .optional();
    }

    /**
     * Subscrição mais recente do prestador, em <b>qualquer</b> estado
     * (inclui terminal) — usada por {@code GET /v1/subscriptions/me}
     * (contrato: "subscrição mais recente ... não só a ativa"). Um prestador
     * pode ter várias linhas ao longo do tempo (uma por ciclo de
     * subscrever/cancelar/subscrever de novo); "mais recente" é definida por
     * {@code created_at DESC}, com {@code id DESC} como desempate
     * determinístico (nenhum dos dois pode empatar sozinho: {@code id} é
     * único; {@code created_at} pode coincidir só sob concorrência
     * extrema, nunca observada nestes testes, mas o {@code ORDER BY}
     * teria de ser estável de qualquer forma).
     *
     * <p><b>Não é um método de gating.</b> Devolve subscrições
     * {@code EXPIRED}/{@code CANCELLED} sem qualquer filtro — a decisão de
     * quem pode operar cabe a {@link #isVisibilityEligible} /
     * {@code hasActiveSubscription}, nunca a este método.
     */
    public Optional<Subscription> findMostRecentByProvider(UUID providerId) {
        return jdbcClient.sql(SELECT + " WHERE provider_id = :providerId ORDER BY created_at DESC, id DESC LIMIT 1")
                .param("providerId", providerId)
                .query(JdbcSubscriptionRepository::mapRow)
                .optional();
    }

    /**
     * Gating de visibilidade resolvido em SQL, usando o fragmento único
     * {@link pt.servimatch.modules.billing.SubscriptionVisibilitySql}
     * (ADR-0011 D4/D5) — a mesma definição que
     * {@code matching}/{@code search} devem consumir, para que a regra não
     * volte a divergir entre este módulo e os seus leitores.
     */
    public boolean isVisibilityEligible(UUID providerId, int visibilityToleranceSeconds) {
        String sql = "SELECT EXISTS (SELECT 1 FROM subscription WHERE provider_id = :providerId AND "
                + SubscriptionVisibilitySql.grantsVisibilityPredicate(null) + ")";
        return Boolean.TRUE.equals(jdbcClient.sql(sql)
                .param("providerId", providerId)
                .param("visibilityToleranceSeconds", visibilityToleranceSeconds)
                .query(Boolean.class)
                .single());
    }

    public Optional<Subscription> findByGatewaySubscriptionId(String gateway, String gatewaySubscriptionId) {
        return jdbcClient.sql(SELECT + " WHERE gateway = :gateway AND gateway_subscription_id = :gatewaySubscriptionId")
                .param("gateway", gateway)
                .param("gatewaySubscriptionId", gatewaySubscriptionId)
                .query(JdbcSubscriptionRepository::mapRow)
                .optional();
    }

    public List<Subscription> findExpiredCandidates(Instant now) {
        return jdbcClient.sql(SELECT + " WHERE status = 'ACTIVE' AND current_period_end IS NOT NULL AND current_period_end < :now")
                .param("now", Timestamp.from(now))
                .query(JdbcSubscriptionRepository::mapRow)
                .list();
    }

    public List<Subscription> findByStatus(SubscriptionStatus status) {
        return jdbcClient.sql(SELECT + " WHERE status = :status")
                .param("status", status.name())
                .query(JdbcSubscriptionRepository::mapRow)
                .list();
    }

    public List<Subscription> findPastDueCandidates() {
        return jdbcClient.sql(SELECT + " WHERE status = 'PAST_DUE'")
                .query(JdbcSubscriptionRepository::mapRow)
                .list();
    }

    public Subscription attachGatewayIds(UUID id, String gatewayCustomerId, String gatewaySubscriptionId) {
        jdbcClient.sql("""
                        UPDATE subscription
                        SET gateway_customer_id = COALESCE(:customerId, gateway_customer_id),
                            gateway_subscription_id = COALESCE(:gatewaySubscriptionId, gateway_subscription_id)
                        WHERE id = :id
                        """)
                .param("customerId", gatewayCustomerId)
                .param("gatewaySubscriptionId", gatewaySubscriptionId)
                .param("id", id)
                .update();
        return findById(id).orElseThrow();
    }

    /**
     * Transição condicional: só aplica se o estado atual estiver em
     * {@code fromStatuses} — guarda de invariante contra eventos tardios
     * que tentem mover uma subscrição já terminal ({@code EXPIRED}/
     * {@code CANCELLED}) ou já no estado-alvo por outra via. Devolve
     * {@code true} apenas se a linha foi de facto alterada.
     */
    public boolean transition(UUID id, List<SubscriptionStatus> fromStatuses, SubscriptionStatus to,
                               Instant periodStart, Instant periodEnd, Boolean cancelAtPeriodEnd) {
        String placeholders = String.join(",", fromStatuses.stream().map(s -> "'" + s.name() + "'").toList());
        int updated = jdbcClient.sql("""
                        UPDATE subscription
                        SET status = :to,
                            current_period_start = COALESCE(:periodStart, current_period_start),
                            current_period_end = COALESCE(:periodEnd, current_period_end),
                            cancel_at_period_end = COALESCE(:cancelAtPeriodEnd, cancel_at_period_end)
                        WHERE id = :id AND status IN (%s)
                        """.formatted(placeholders))
                .param("to", to.name())
                .param("periodStart", periodStart == null ? null : Timestamp.from(periodStart))
                .param("periodEnd", periodEnd == null ? null : Timestamp.from(periodEnd))
                .param("cancelAtPeriodEnd", cancelAtPeriodEnd)
                .param("id", id)
                .update();
        return updated > 0;
    }

    private static final String SELECT = """
            SELECT id, provider_id, plan_id, status, gateway, gateway_customer_id, gateway_subscription_id,
                   current_period_start, current_period_end, cancel_at_period_end, created_at, updated_at
            FROM subscription
            """;

    private static Subscription mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Subscription(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("provider_id"),
                (UUID) rs.getObject("plan_id"),
                SubscriptionStatus.valueOf(rs.getString("status")),
                rs.getString("gateway"),
                rs.getString("gateway_customer_id"),
                rs.getString("gateway_subscription_id"),
                toInstant(rs.getTimestamp("current_period_start")),
                toInstant(rs.getTimestamp("current_period_end")),
                rs.getBoolean("cancel_at_period_end"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
