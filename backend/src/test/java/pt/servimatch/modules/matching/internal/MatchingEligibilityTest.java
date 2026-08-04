package pt.servimatch.modules.matching.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import pt.servimatch.modules.geo.GeoPoint;
import pt.servimatch.modules.matching.EligibilityQuery;
import pt.servimatch.modules.matching.MatchingApi;
import pt.servimatch.testsupport.TestDatabase;

/**
 * Prova o predicado de elegibilidade (ADR-0004) contra PostGIS real
 * ({@code pt.servimatch.testsupport.SharedPostgis} — container partilhado,
 * migrações V1–V14 aplicadas uma vez para toda a JVM de testes). Sem
 * contexto Spring: {@link EligibilityRepository}/{@link MatchingService}
 * são instanciados diretamente com um {@link JdbcClient} ligado ao
 * container, ao mesmo padrão usado por
 * {@code pt.servimatch.modules.payments.PaymentsApiIntegrationTest} para o
 * acesso a dados — mais rápido do que arrancar
 * {@code ServiMatchApplication} para algo que não precisa do resto da app.
 *
 * <p>Casos funcionais: raio dentro/fora/na fronteira exata, exclusão por
 * subscrição inativa, exclusão por aprovação pendente, região
 * correspondente e não correspondente.
 *
 * <p><b>A parte que interessa de facto</b> (ver javadoc de
 * {@code pt.servimatch.modules.geo.CoverageSql}):
 * {@link #radiusPrefilterProducesAnIndexCondNotJustAFilter()} e
 * {@link #candidatesCteIsGenuinelyMaterialized()} correm
 * {@code EXPLAIN (ANALYZE, BUFFERS)} sobre a SQL real de
 * {@link EligibilityRepository#FIND_ELIGIBLE_PROVIDER_IDS_SQL} (não uma
 * cópia à mão) com ~20 000 prestadores semeados, e falham se alguém
 * reverter o pré-filtro de raio constante ou o qualificador
 * {@code MATERIALIZED} em {@code CoverageSql} — os dois fixes que tornam o
 * índice GiST de {@code idx_provider_service_area_center} realmente
 * efetivo, em vez de um varrimento completo disfarçado de "Index Scan".
 * Um teste que só confirmasse "devolve os prestadores certos" passaria
 * igualmente com a versão ~10× mais lenta (confirmado manualmente: ver
 * relatório da tarefa) — por isso essas duas asserções são sobre a
 * <em>forma do plano</em>, não sobre tempo de execução (que varia com a
 * máquina) nem sobre o resultado (que a versão lenta também acerta).
 */
class MatchingEligibilityTest {

    private static final JdbcClient JDBC = TestDatabase.jdbcClient();
    private static final MatchingApi MATCHING_API = new MatchingService(new EligibilityRepository(JDBC));

    private static final UUID CATEGORY_ID = UUID.randomUUID();

    // Lisboa (Rossio) como ponto de referência do pedido.
    private static final GeoPoint LISBON = new GeoPoint(38.7139, -9.1394);

    // Centro de cobertura usado pelos prestadores de raio deste teste.
    // ST_Distance(centro, LISBON) sobre geography = 335.857...m — confirmado
    // manualmente com ST_Distance antes de escrever este teste (ver
    // relatório da tarefa), para poder escolher um radius_m de fronteira
    // exata sem adivinhar.
    private static final double AREA_CENTER_LAT = 38.7169;
    private static final double AREA_CENTER_LON = -9.1399;
    private static final int EXACT_DISTANCE_TO_LISBON_M = 336; // arredondado para cima de 335.857...

    private static UUID providerInsideRadius;
    private static UUID providerOutsideRadius;
    private static UUID providerExactBoundary;
    private static UUID providerNoActiveSubscription;
    private static UUID providerInPastDueGracePeriod;
    private static UUID providerPastDueGraceExpired;
    private static UUID providerNotApproved;
    private static UUID providerMatchingRegion;
    private static UUID providerNonMatchingRegion;

    @BeforeAll
    static void seed() {
        JDBC.sql("INSERT INTO category (id, slug, name) VALUES (:id, :slug, 'Canalização IT')")
                .param("id", CATEGORY_ID)
                .param("slug", "canalizacao-it-" + CATEGORY_ID)
                .update();
        UUID planId = TestDatabase.createPlan(JDBC, 0);

        // Volume representativo (ADR/skill: "com poucas linhas o planeador
        // prefere seq scan e o teste não prova nada"): confirmado
        // manualmente que 20k prestadores reproduz de forma fiável a
        // diferença de plano; ver relatório da tarefa.
        seedBulkFillerProviders(planId, 20_000);

        providerInsideRadius = seedProvider(planId, "Dentro do raio", "APPROVED", "ACTIVE");
        providerOutsideRadius = seedProvider(planId, "Fora do raio", "APPROVED", "ACTIVE");
        providerExactBoundary = seedProvider(planId, "Fronteira exata", "APPROVED", "ACTIVE");
        // Estado terminal (não PAST_DUE): sob ADR-0011 §D4, PAST_DUE concede
        // grace period, pelo que só um estado terminal (CANCELLED/EXPIRED)
        // prova genuinamente "sem subscrição que conceda visibilidade" — ver
        // providerInPastDueGracePeriod/providerPastDueGraceExpired abaixo
        // para os dois casos que PAST_DUE de facto cobre.
        providerNoActiveSubscription = seedProvider(planId, "Sem subscrição ativa", "APPROVED", "CANCELLED");
        // Grace period (ADR-0011 §D4/ARQUITETURA §12.1): PAST_DUE com
        // current_period_end ainda no futuro continua a conceder
        // visibilidade — uma falha de cartão não desliga o prestador no
        // mesmo instante.
        providerInPastDueGracePeriod =
                seedProvider(planId, "Grace period ativo", "APPROVED", "PAST_DUE", "now() + interval '30 days'");
        // Fim do grace (ADR-0011 §D5): PAST_DUE cujo current_period_end já
        // passou deixa de conceder visibilidade — o predicado não pode
        // depender só do estado, sob pena de o job de expiração atrasar e o
        // prestador continuar a operar sem pagar (falha aberta).
        providerPastDueGraceExpired =
                seedProvider(planId, "Grace period expirado", "APPROVED", "PAST_DUE", "now() - interval '1 day'");
        providerNotApproved = seedProvider(planId, "Não aprovado", "PENDING", "ACTIVE");
        providerMatchingRegion = seedProvider(planId, "Região correspondente", "APPROVED", "ACTIVE");
        providerNonMatchingRegion = seedProvider(planId, "Região não correspondente", "APPROVED", "ACTIVE");

        insertRadiusArea(providerInsideRadius, 5_000); // ~336m do ponto, bem dentro
        insertRadiusArea(providerOutsideRadius, 100); // mesmo centro, raio pequeno demais
        // ST_DWithin é <=, não <; um raio que termina exatamente na
        // distância ao ponto tem de incluir, não excluir (fronteira fechada).
        insertRadiusArea(providerExactBoundary, EXACT_DISTANCE_TO_LISBON_M);
        insertRadiusArea(providerNoActiveSubscription, 5_000);
        insertRadiusArea(providerInPastDueGracePeriod, 5_000);
        insertRadiusArea(providerPastDueGraceExpired, 5_000);
        insertRadiusArea(providerNotApproved, 5_000);

        insertRegionArea(providerMatchingRegion, "PT-11-LSB");
        insertRegionArea(providerNonMatchingRegion, "PT-13-PRT");

        JDBC.sql("ANALYZE provider_service_area").update();
        JDBC.sql("ANALYZE provider_profile").update();
        JDBC.sql("ANALYZE subscription").update();
        JDBC.sql("ANALYZE provider_category").update();
    }

    @Test
    void findsProviderInsideRadius() {
        var eligible = MATCHING_API.findEligibleProviderIds(CATEGORY_ID, LISBON, null);

        assertThat(eligible).contains(providerInsideRadius);
    }

    @Test
    void excludesProviderOutsideRadius() {
        var eligible = MATCHING_API.findEligibleProviderIds(CATEGORY_ID, LISBON, null);

        assertThat(eligible).doesNotContain(providerOutsideRadius);
    }

    @Test
    void includesProviderAtOrNearTheExactRadiusBoundary() {
        var eligible = MATCHING_API.findEligibleProviderIds(CATEGORY_ID, LISBON, null);

        assertThat(eligible).contains(providerExactBoundary);
    }

    @Test
    void excludesProviderWithoutActiveSubscription() {
        var eligible = MATCHING_API.findEligibleProviderIds(CATEGORY_ID, LISBON, null);

        assertThat(eligible).doesNotContain(providerNoActiveSubscription);
    }

    @Test
    void includesProviderInPastDueGracePeriod() {
        var eligible = MATCHING_API.findEligibleProviderIds(CATEGORY_ID, LISBON, null);

        assertThat(eligible).contains(providerInPastDueGracePeriod);
    }

    @Test
    void excludesProviderWhosePastDueGracePeriodHasExpired() {
        var eligible = MATCHING_API.findEligibleProviderIds(CATEGORY_ID, LISBON, null);

        assertThat(eligible).doesNotContain(providerPastDueGraceExpired);
    }

    @Test
    void excludesProviderNotApproved() {
        var eligible = MATCHING_API.findEligibleProviderIds(CATEGORY_ID, LISBON, null);

        assertThat(eligible).doesNotContain(providerNotApproved);
    }

    @Test
    void findsProviderByMatchingAdminRegionAndExcludesNonMatching() {
        var eligible = MATCHING_API.findEligibleProviderIds(CATEGORY_ID, null, "PT-11-LSB");

        assertThat(eligible).contains(providerMatchingRegion).doesNotContain(providerNonMatchingRegion);
    }

    @Test
    void excludesEverythingWhenRegionDoesNotMatchAnyProvider() {
        var eligible = MATCHING_API.findEligibleProviderIds(CATEGORY_ID, null, "PT-08-FAR");

        assertThat(eligible).doesNotContain(providerMatchingRegion, providerNonMatchingRegion);
    }

    @Test
    void isEligibleAgreesWithFindEligibleProviderIdsForASingleProvider() {
        boolean eligible = MATCHING_API.isEligible(
                new EligibilityQuery(providerInsideRadius, CATEGORY_ID, LISBON, null));

        assertThat(eligible).isTrue();
    }

    @Test
    void isEligibleIsFalseForAProviderExcludedByApproval() {
        boolean eligible = MATCHING_API.isEligible(
                new EligibilityQuery(providerNotApproved, CATEGORY_ID, LISBON, null));

        assertThat(eligible).isFalse();
    }

    @Test
    void radiusPrefilterProducesAnIndexCondNotJustAFilter() {
        String plan = explain(EligibilityRepository.FIND_ELIGIBLE_PROVIDER_IDS_SQL);

        // O pré-filtro de raio constante (CoverageSql.MAX_RADIUS_METERS)
        // é o que permite ao PostGIS reescrever a condição como
        // "center && _st_expand(ponto, raio constante)" e usá-la como
        // Index Cond do GiST — sem ele, ST_DWithin com o raio por linha
        // (radius_m) aparece só como Filter (varrimento completo do
        // índice, confirmado manualmente: ~6700 buffers vs ~2500). Se
        // alguém remover esse ST_DWithin extra de CoverageSql, "_st_expand"
        // desaparece do plano e este teste falha.
        assertThat(plan)
                .as("plano de EXPLAIN:%n%s", plan)
                .contains("idx_provider_service_area_center")
                .contains("_st_expand");
    }

    @Test
    void candidatesCteIsGenuinelyMaterialized() {
        String plan = explain(EligibilityRepository.FIND_ELIGIBLE_PROVIDER_IDS_SQL);

        // MATERIALIZED só aparece no plano como um nó próprio ("CTE Scan on
        // candidates"); sem a palavra-chave, o PostgreSQL pode embutir a
        // condição geográfica na consulta principal e reavaliá-la por linha
        // externa (confirmado manualmente com 20 000 prestadores: sem
        // MATERIALIZED nem pré-filtro, o nó de provider_service_area mostra
        // loops=17066 e ~1.5s; com ambos, loops=1 e ~150ms — ver javadoc de
        // CoverageSql). Se alguém remover MATERIALIZED de CoverageSql, este
        // nó desaparece do plano e este teste falha.
        assertThat(plan)
                .as("plano de EXPLAIN:%n%s", plan)
                .contains("CTE Scan on candidates");
    }

    private static String explain(String sql) {
        return String.join("\n", JDBC.sql("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql)
                .param("categoryId", CATEGORY_ID)
                .param("lat", LISBON.lat(), java.sql.Types.DOUBLE)
                .param("lon", LISBON.lon(), java.sql.Types.DOUBLE)
                .param("regionCode", (String) null, java.sql.Types.VARCHAR)
                .param("maxRadiusM", pt.servimatch.modules.geo.CoverageSql.MAX_RADIUS_METERS)
                .param("visibilityToleranceSeconds",
                        pt.servimatch.modules.billing.SubscriptionVisibilitySql.DEFAULT_PERIOD_END_TOLERANCE_SECONDS)
                .query(String.class)
                .list());
    }

    private static UUID seedProvider(UUID planId, String headline, String approvalStatus, String subscriptionStatus) {
        return seedProvider(planId, headline, approvalStatus, subscriptionStatus, "now() + interval '30 days'");
    }

    /**
     * @param currentPeriodEndExpression expressão SQL literal (não
     *                                    vinculada) para {@code current_period_end} — usada pelos
     *                                    casos de fronteira do grace period de {@code PAST_DUE}
     *                                    (ADR-0011 §D5); nunca vem de entrada externa, só de
     *                                    literais fixos definidos neste teste.
     *
     *                                    <p>{@code approval_status='APPROVED'} por {@code INSERT}
     *                                    direto (quando {@code approvalStatus} não é {@code PENDING})
     *                                    é um atalho de setup (ADR-0011 D9) — a transição real
     *                                    ({@code PENDING → APPROVED} via {@code PATCH
     *                                    /v1/admin/providers/{id}/approval}) tem o seu teste próprio
     *                                    em {@code
     *                                    pt.servimatch.modules.providers.ProviderApprovalIntegrationTest}
     *                                    e, atravessando pesquisa/inbox, em {@code
     *                                    pt.servimatch.gating.ProviderApprovalUnlocksSearchAndMatchingIntegrationTest}.
     *                                    {@code approval_decided_by}/{@code approval_decided_at} só
     *                                    satisfazem aqui o {@code CHECK
     *                                    chk_provider_profile_approval_decision_coherence} (V22) para
     *                                    os casos {@code APPROVED}; ficam {@code NULL} para
     *                                    {@code PENDING} (nunca houve decisão) — reutiliza-se o
     *                                    próprio {@code userId} do prestador como autor fictício.
     */
    private static UUID seedProvider(
            UUID planId,
            String headline,
            String approvalStatus,
            String subscriptionStatus,
            String currentPeriodEndExpression) {
        UUID userId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        JDBC.sql("INSERT INTO users (id, keycloak_sub, email, display_name) VALUES (:id, :sub, :email, :name)")
                .param("id", userId)
                .param("sub", "it-" + userId)
                .param("email", userId + "@example.test")
                .param("name", headline)
                .update();
        // Sem visibility_state (ADR-0011 §D1): a coluna deixou de ser lida
        // pelo predicado de produção, e este teste não a fabrica.
        // approval_decided_by/at ficam NULL para PENDING (nunca houve
        // decisão) e preenchidos nos restantes casos, só para satisfazer o
        // CHECK chk_provider_profile_approval_decision_coherence (V22) — sem
        // passar null diretamente ao driver, que exige tipo explícito.
        JDBC.sql("""
                INSERT INTO provider_profile (id, user_id, headline, approval_status, approval_decided_by, approval_decided_at)
                VALUES (:id, :userId, :headline, :approval,
                        CASE WHEN :approval = 'PENDING' THEN NULL ELSE :userId END,
                        CASE WHEN :approval = 'PENDING' THEN NULL ELSE now() END)
                """)
                .param("id", providerId)
                .param("userId", userId)
                .param("headline", headline)
                .param("approval", approvalStatus)
                .update();
        JDBC.sql("INSERT INTO provider_category (provider_id, category_id) VALUES (:p, :c)")
                .param("p", providerId).param("c", CATEGORY_ID).update();
        JDBC.sql(("""
                INSERT INTO subscription (provider_id, plan_id, status, gateway, current_period_start, current_period_end)
                VALUES (:p, :plan, :status, 'stripe', now(), %s)
                """).formatted(currentPeriodEndExpression))
                .param("p", providerId).param("plan", planId).param("status", subscriptionStatus).update();
        return providerId;
    }

    private static void insertRadiusArea(UUID providerId, int radiusM) {
        JDBC.sql("""
                INSERT INTO provider_service_area (provider_id, mode, center, radius_m)
                VALUES (:p, 'RADIUS', ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius)
                """)
                .param("p", providerId)
                .param("lat", AREA_CENTER_LAT)
                .param("lon", AREA_CENTER_LON)
                .param("radius", radiusM)
                .update();
    }

    private static void insertRegionArea(UUID providerId, String regionCode) {
        JDBC.sql("INSERT INTO provider_service_area (provider_id, mode, region_code) VALUES (:p, 'ADMIN_REGION', :r)")
                .param("p", providerId).param("r", regionCode).update();
    }

    /**
     * Prestadores de enchimento, sem papel nas asserções funcionais: dão à
     * consulta o volume necessário para que o planeador do PostgreSQL
     * escolha entre um varrimento seletivo e um varrimento completo — com
     * poucas linhas ele prefere sempre seq scan e as duas asserções de
     * EXPLAIN não provariam nada (skill {@code postgis-geo-matching}).
     * Espalhados por um bbox largo (~200km x ~300km) para que a maioria não
     * caia por acaso no raio/região exatos usados pelos prestadores
     * nomeados acima.
     */
    private static void seedBulkFillerProviders(UUID planId, int count) {
        JDBC.sql("""
                INSERT INTO users (id, keycloak_sub, email, display_name)
                SELECT gen_random_uuid(), 'it-bulk-' || :categoryId || '-' || gs, 'it-bulk-' || gs || '@example.test', 'Bulk ' || gs
                FROM generate_series(1, :count) gs
                """).param("categoryId", CATEGORY_ID).param("count", count).update();

        // Todos APPROVED (fins de volume/plano, não de asserção funcional —
        // ver javadoc da classe): approval_decided_by/at preenchidos só para
        // satisfazer o CHECK chk_provider_profile_approval_decision_coherence
        // (V22), mesmo atalho de setup de seedProvider acima.
        JDBC.sql("""
                INSERT INTO provider_profile (id, user_id, headline, approval_status, approval_decided_by, approval_decided_at, rating_avg, rating_count)
                SELECT gen_random_uuid(), u.id, 'Bulk provider', 'APPROVED', u.id, now(),
                       (random() * 5)::numeric(3,2), (random() * 200)::int
                FROM users u WHERE u.keycloak_sub LIKE 'it-bulk-' || :categoryId || '-%'
                """).param("categoryId", CATEGORY_ID).update();

        JDBC.sql("""
                INSERT INTO provider_category (provider_id, category_id)
                SELECT p.id, :categoryId
                FROM provider_profile p JOIN users u ON u.id = p.user_id
                WHERE u.keycloak_sub LIKE 'it-bulk-' || :categoryId || '-%'
                """).param("categoryId", CATEGORY_ID).update();

        JDBC.sql("""
                INSERT INTO subscription (provider_id, plan_id, status, gateway, current_period_start, current_period_end)
                SELECT p.id, :plan, CASE WHEN random() < 0.85 THEN 'ACTIVE' ELSE 'PENDING' END,
                       'stripe', now(), now() + interval '30 days'
                FROM provider_profile p JOIN users u ON u.id = p.user_id
                WHERE u.keycloak_sub LIKE 'it-bulk-' || :categoryId || '-%'
                """).param("categoryId", CATEGORY_ID).param("plan", planId).update();

        JDBC.sql("""
                INSERT INTO provider_service_area (provider_id, mode, center, radius_m)
                SELECT p.id, 'RADIUS',
                       ST_SetSRID(ST_MakePoint(-9.5 + random() * 2.0, 38.5 + random() * 3.0), 4326)::geography,
                       (1000 + random() * 29000)::int
                FROM provider_profile p JOIN users u ON u.id = p.user_id
                WHERE u.keycloak_sub LIKE 'it-bulk-' || :categoryId || '-%' AND random() < 0.5
                """).param("categoryId", CATEGORY_ID).update();

        JDBC.sql("""
                INSERT INTO provider_service_area (provider_id, mode, region_code)
                SELECT p.id, 'ADMIN_REGION',
                       (ARRAY['PT-11-LSB','PT-13-PRT','PT-08-FAR','PT-01-AVR'])[1 + floor(random() * 4)::int]
                FROM provider_profile p
                JOIN users u ON u.id = p.user_id
                WHERE u.keycloak_sub LIKE 'it-bulk-' || :categoryId || '-%'
                  AND p.id NOT IN (SELECT provider_id FROM provider_service_area)
                """).param("categoryId", CATEGORY_ID).update();
    }
}
