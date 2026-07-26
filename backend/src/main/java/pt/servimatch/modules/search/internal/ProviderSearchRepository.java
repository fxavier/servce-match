package pt.servimatch.modules.search.internal;

import java.sql.Types;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.servimatch.modules.geo.CoverageSql;
import pt.servimatch.modules.geo.GeoPoint;
import pt.servimatch.modules.search.internal.dto.ProviderSummary;

/**
 * Consulta SQL nativa de {@code GET /v1/search/providers} (ADR-0004,
 * ADR-0005). Reutiliza {@link CoverageSql} para o filtro geográfico — a
 * mesma condição usada pelo predicado central de {@code matching}, sem a
 * duplicar.
 *
 * <p><b>Ordenação (determinística, critério de desempate explícito):</b>
 * {@code subscription_plan.ranking_boost DESC} (Premium primeiro, ADR
 * §3.4/§10.3) → relevância textual ({@code ts_rank}, só quando {@code q} é
 * dado) DESC → {@code rating_avg} DESC → {@code id} ASC (desempate final,
 * estável, necessário para que a paginação por cursor nunca repita nem
 * salte linhas entre páginas consecutivas).
 */
@Repository
class ProviderSearchRepository {

    private final JdbcClient jdbcClient;

    // @Lazy: ver o mesmo comentário em matching.internal.EligibilityRepository
    // — pt.servimatch.config.SecurityConfigTest arranca o contexto completo
    // sem DataSource; não afeta o comportamento em produção.
    ProviderSearchRepository(@Lazy JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    List<ProviderSummary> search(
            UUID categoryId, GeoPoint point, String regionCode, String q, int limit, int offset) {

        boolean hasGeoFilter = point != null || regionCode != null;

        StringBuilder sql = new StringBuilder();
        if (hasGeoFilter) {
            sql.append("WITH ").append(CoverageSql.CANDIDATE_PROVIDER_IDS_CTE).append('\n');
        }
        sql.append("""
                SELECT p.id, u.display_name, p.headline, comp.name AS company_name,
                       p.rating_avg, p.rating_count, p.verified,
                       COALESCE(sp.has_badge, false) AS premium_badge
                FROM provider_profile p
                JOIN users u ON u.id = p.user_id
                LEFT JOIN company comp ON comp.id = p.company_id
                JOIN subscription s ON s.provider_id = p.id AND s.status = 'ACTIVE'
                JOIN subscription_plan sp ON sp.id = s.plan_id
                WHERE p.approval_status = 'APPROVED'
                  AND p.visibility_state = 'VISIBLE'
                """);
        if (hasGeoFilter) {
            sql.append("  AND p.id IN (SELECT provider_id FROM candidates)\n");
        }
        sql.append("""
                  AND (:categoryId::uuid IS NULL OR EXISTS (
                        SELECT 1 FROM provider_category pc
                        WHERE pc.provider_id = p.id AND pc.category_id = :categoryId::uuid
                      ))
                  AND (:q::text IS NULL OR (
                        p.search_tsv @@ websearch_to_tsquery('portuguese', immutable_unaccent(:q::text))
                        OR p.headline % :q::text
                      ))
                ORDER BY sp.ranking_boost DESC,
                         CASE WHEN :q::text IS NULL THEN 0
                              ELSE ts_rank(p.search_tsv, websearch_to_tsquery('portuguese', immutable_unaccent(:q::text)))
                         END DESC,
                         p.rating_avg DESC,
                         p.id ASC
                LIMIT :limit::int OFFSET :offset::int
                """);

        var statement = jdbcClient.sql(sql.toString())
                .param("categoryId", categoryId, Types.OTHER)
                .param("q", q, Types.VARCHAR)
                .param("limit", limit)
                .param("offset", offset);
        if (hasGeoFilter) {
            statement = statement
                    .param("lat", point == null ? null : point.lat(), Types.DOUBLE)
                    .param("lon", point == null ? null : point.lon(), Types.DOUBLE)
                    .param("regionCode", regionCode, Types.VARCHAR)
                    .param("maxRadiusM", CoverageSql.MAX_RADIUS_METERS);
        }

        return statement
                .query((rs, rowNum) -> new ProviderSummary(
                        (UUID) rs.getObject("id"),
                        rs.getString("display_name"),
                        rs.getString("headline"),
                        rs.getString("company_name"),
                        rs.getFloat("rating_avg"),
                        rs.getInt("rating_count"),
                        rs.getBoolean("verified"),
                        rs.getBoolean("premium_badge"),
                        null // avatarUrl: sem coluna no esquema atual — ver relatório da tarefa.
                ))
                .list();
    }
}
