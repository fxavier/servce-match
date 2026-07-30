package pt.servimatch.modules.matching.internal;

import java.sql.Types;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.servimatch.modules.billing.SubscriptionVisibilitySql;
import pt.servimatch.modules.geo.CoverageSql;
import pt.servimatch.modules.geo.GeoPoint;

/**
 * Acesso SQL nativo ao predicado de elegibilidade (ADR-0004 §10.3, composto
 * com P1 do ADR-0011 §D2/§D4). Consulta diretamente as tabelas de
 * {@code providers} e {@code subscriptions} (esquema partilhado, sem
 * importar classes Java de {@code providers} — ver {@code docs/adr/} e a
 * nota de "SQL nativo" no relatório da tarefa); a exceção deliberada é
 * {@link SubscriptionVisibilitySql}, que não é uma leitura de esquema mas o
 * fragmento único e reutilizável do predicado "esta subscrição concede
 * visibilidade agora" (ADR-0011 §D4) — consumi-lo aqui, em vez de copiar o
 * literal de estados, é precisamente o que a decisão exige.
 *
 * <p><b>{@code visibility_state} não é lido aqui (ADR-0011 §D1).</b> Antes
 * desta correção o {@code WHERE} incluía
 * {@code p.visibility_state = 'VISIBLE'} — uma coluna sem nenhum escritor em
 * produção (sempre {@code 'HIDDEN'}, o {@code DEFAULT} da V4) que negava
 * silenciosamente todo o predicado de elegibilidade: nenhum prestador,
 * subscrito ou não, alguma vez passava. Ver
 * {@code pt.servimatch.gating.ProviderVisibilityWithoutBillingListenerIntegrationTest}
 * para a reprodução.
 */
@Repository
class EligibilityRepository {

    /**
     * Pacote-privado (não {@code private}) deliberadamente: é a mesma
     * string SQL que {@link MatchingEligibilityTest} passa a
     * {@code EXPLAIN (ANALYZE, BUFFERS)} para provar o uso do índice GiST.
     * Sem esta partilha, um teste que reconstruísse a query "à mão" podia
     * ficar a testar uma cópia, não o código real — e passar mesmo depois
     * de alguém reverter o pré-filtro ou o {@code MATERIALIZED} em
     * {@link CoverageSql}.
     */
    static final String FIND_ELIGIBLE_PROVIDER_IDS_SQL = """
            WITH %s
            SELECT p.id
            FROM candidates c
            JOIN provider_profile p ON p.id = c.provider_id
            WHERE p.approval_status = 'APPROVED'
              AND EXISTS (
                    SELECT 1 FROM subscription s
                    WHERE s.provider_id = p.id AND %s
                  )
              AND EXISTS (
                    SELECT 1 FROM provider_category pc
                    WHERE pc.provider_id = p.id AND pc.category_id = :categoryId::uuid
                  )
            """.formatted(CoverageSql.CANDIDATE_PROVIDER_IDS_CTE, SubscriptionVisibilitySql.grantsVisibilityPredicate("s"));

    private final JdbcClient jdbcClient;

    EligibilityRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Verificação pontual para um único prestador. Não usa a CTE
     * materializada de {@link CoverageSql} — com um único prestador
     * (1–2 linhas de {@code provider_service_area}), o índice GiST não traz
     * benefício e o predicado direto é suficiente.
     */
    boolean isEligible(UUID providerId, UUID categoryId, GeoPoint point, String regionCode) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM provider_profile p
                    WHERE p.id = :providerId::uuid
                      AND p.approval_status = 'APPROVED'
                      AND EXISTS (
                            SELECT 1 FROM subscription s
                            WHERE s.provider_id = p.id AND %s
                          )
                      AND EXISTS (
                            SELECT 1 FROM provider_category pc
                            WHERE pc.provider_id = p.id AND pc.category_id = :categoryId::uuid
                          )
                      AND EXISTS (
                            SELECT 1 FROM provider_service_area a
                            WHERE a.provider_id = p.id
                              AND (
                                    (a.mode = 'RADIUS'
                                        AND ST_DWithin(a.center, ST_SetSRID(ST_MakePoint(:lon::float8, :lat::float8), 4326)::geography, a.radius_m))
                                 OR (a.mode = 'ADMIN_REGION' AND a.region_code = :regionCode::text)
                              )
                          )
                )
                """.formatted(SubscriptionVisibilitySql.grantsVisibilityPredicate("s"));
        Boolean result = jdbcClient.sql(sql)
                .param("providerId", providerId)
                .param("categoryId", categoryId)
                .param("lat", point == null ? null : point.lat(), Types.DOUBLE)
                .param("lon", point == null ? null : point.lon(), Types.DOUBLE)
                .param("regionCode", regionCode, Types.VARCHAR)
                .param("visibilityToleranceSeconds", SubscriptionVisibilitySql.DEFAULT_PERIOD_END_TOLERANCE_SECONDS)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(result);
    }

    /**
     * Predicado central do ADR-0004: prestadores elegíveis para uma
     * categoria e localização/região, devolvidos como conjunto (uma única
     * consulta, sem N+1). Usa a CTE materializada de {@link CoverageSql}
     * para que o índice GiST de {@code provider_service_area.center} seja
     * efetivamente usado — ver javadoc de {@link CoverageSql} para a
     * justificação, provada por {@code EXPLAIN}.
     */
    Set<UUID> findEligibleProviderIds(UUID categoryId, GeoPoint point, String regionCode) {
        List<UUID> ids = jdbcClient.sql(FIND_ELIGIBLE_PROVIDER_IDS_SQL)
                .param("categoryId", categoryId)
                .param("lat", point == null ? null : point.lat(), Types.DOUBLE)
                .param("lon", point == null ? null : point.lon(), Types.DOUBLE)
                .param("regionCode", regionCode, Types.VARCHAR)
                .param("maxRadiusM", CoverageSql.MAX_RADIUS_METERS)
                .param("visibilityToleranceSeconds", SubscriptionVisibilitySql.DEFAULT_PERIOD_END_TOLERANCE_SECONDS)
                .query((rs, rowNum) -> (UUID) rs.getObject("id"))
                .list();
        return Set.copyOf(ids);
    }

    /**
     * De um lote de pedidos candidatos, os que o prestador cobre
     * geograficamente. Sem necessidade da CTE/pré-filtro de
     * {@link CoverageSql}: aqui a cardinalidade da área do prestador é
     * pequena (1–2 linhas, filtradas por {@code provider_id} — já indexado
     * por {@code idx_provider_service_area_provider_id}); o volume a
     * discriminar está do lado de {@code service_request}, cujo
     * {@code location} já tem o seu próprio índice GiST
     * ({@code idx_service_request_location}) — não é este método que
     * precisa de o forçar por não ser ele o lado que faz a procura espacial
     * em massa.
     */
    Set<UUID> filterEligibleRequestIds(UUID providerId, List<UUID> candidateRequestIds) {
        String sql = """
                SELECT sr.id
                FROM service_request sr
                WHERE sr.id IN (:candidateIds)
                  AND EXISTS (
                        SELECT 1 FROM provider_service_area a
                        WHERE a.provider_id = :providerId::uuid
                          AND (
                                (a.mode = 'RADIUS' AND sr.location IS NOT NULL
                                    AND ST_DWithin(sr.location, a.center, a.radius_m))
                             OR (a.mode = 'ADMIN_REGION' AND sr.address_region_code IS NOT NULL
                                    AND a.region_code = sr.address_region_code)
                          )
                      )
                """;
        List<UUID> ids = jdbcClient.sql(sql)
                .param("candidateIds", candidateRequestIds)
                .param("providerId", providerId)
                .query((rs, rowNum) -> (UUID) rs.getObject("id"))
                .list();
        return Set.copyOf(ids);
    }
}
