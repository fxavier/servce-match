package pt.servimatch.modules.providers.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Acesso a {@code provider_profile} (V4, V19), {@code provider_category}
 * (V4), {@code provider_service_area} (V4, V16) e {@code provider_portfolio}
 * (V18) — as tabelas do agregado "providers". {@code company} não é lido
 * aqui — ver nota em {@link #findById}.
 *
 * <p><b>{@code review} (V10, tabela do módulo {@code reviews}) em
 * {@link #ratingDistribution}.</b> Leitura entre módulos autorizada sob os
 * critérios do ADR-0010: (a) consulta <em>set-based</em> — uma única
 * agregação {@code count(*) FILTER}, nunca cinco consultas nem N chamadas a
 * uma API por prestador; a alternativa por API Java (se {@code reviews}
 * expusesse este agregado) não elimina o custo, só o desloca — continuaria a
 * ser uma consulta a esta mesma tabela; (b) esta nota é a declaração exigida
 * pela alínea (b) do ADR, nomeando a tabela e o módulo dono; (c) coberta por
 * {@code ProvidersControllerIntegrationTest} contra Postgres real. Esta
 * leitura <b>não está</b> no quadro enumerado do ADR-0010 — está aqui porque
 * não existe hoje nenhuma API pública de {@code reviews} para este agregado
 * (achado ainda em aberto, ADR-0011 D9) e {@code providers → reviews}
 * fecharia um ciclo com {@code reviews → providers}, já em vigor (ver
 * {@code providers.package-info}). Reportado ao {@code arquiteto} para
 * decidir entre acrescentar esta linha ao quadro do ADR-0010 ou pedir a
 * {@code reviews} que publique {@code ReviewsApi.ratingDistribution} —
 * decisão fora do âmbito deste agente (ver relatório de entrega).
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

    /**
     * Tradução/resumo em lote — uma única consulta {@code IN (:ids)}. Ver
     * {@code ProvidersApi#findUserIdsByProviderIds}/{@code #summaries}.
     * Chamador garante {@code ids} não vazio.
     */
    List<ProviderProfileRow> findByIds(Set<UUID> ids) {
        return jdbcClient.sql(SELECT_BASE + " WHERE p.id IN (:ids)")
                .param("ids", ids)
                .query(this::mapRow)
                .list();
    }

    Set<UUID> findWorkedCategoryIds(UUID providerId) {
        return Set.copyOf(jdbcClient.sql("SELECT category_id FROM provider_category WHERE provider_id = :providerId")
                .param("providerId", providerId)
                .query((rs, rowNum) -> (UUID) rs.getObject("category_id"))
                .list());
    }

    /** {@code region_code} das zonas em modo {@code ADMIN_REGION} (V4) — o modo {@code RADIUS} não tem código de região. */
    List<String> findAdminRegionCodes(UUID providerId) {
        return jdbcClient.sql("""
                        SELECT region_code FROM provider_service_area
                        WHERE provider_id = :providerId AND mode = 'ADMIN_REGION'
                        ORDER BY region_code
                        """)
                .param("providerId", providerId)
                .query(String.class)
                .list();
    }

    /** Ordem de posição declarada em {@code PUT /v1/providers/me} (V18); {@code imageAssetId} resolvido depois via {@code UploadsApi#resolve}. */
    List<PortfolioImageRow> findPortfolio(UUID providerId) {
        return jdbcClient.sql("""
                        SELECT image_asset_id, position FROM provider_portfolio
                        WHERE provider_id = :providerId
                        ORDER BY position
                        """)
                .param("providerId", providerId)
                .query((rs, rowNum) -> new PortfolioImageRow((UUID) rs.getObject("image_asset_id"), rs.getInt("position")))
                .list();
    }

    /**
     * Distribuição de estrelas — <b>uma</b> consulta agregada
     * ({@code count(*) FILTER}), nunca cinco. {@code targetUserId} é
     * {@code users.id}, não {@code provider_profile.id}: {@code review.target_id}
     * (V10) referencia {@code users}, porque uma avaliação pode ter como alvo
     * tanto um prestador como um cliente (avaliação bilateral, ver comentário
     * da V10) — o chamador tem de passar {@code ProviderProfileRow.userId()},
     * nunca o {@code providerId}. Devolve sempre uma linha, com zeros quando o
     * prestador não tem avaliações (agregação sem {@code GROUP BY} nunca
     * devolve conjunto vazio). Ver ADR-0010 no javadoc da classe.
     */
    RatingDistributionRow ratingDistribution(UUID targetUserId) {
        return jdbcClient.sql("""
                        SELECT
                            count(*) FILTER (WHERE rating = 5) AS r5,
                            count(*) FILTER (WHERE rating = 4) AS r4,
                            count(*) FILTER (WHERE rating = 3) AS r3,
                            count(*) FILTER (WHERE rating = 2) AS r2,
                            count(*) FILTER (WHERE rating = 1) AS r1
                        FROM review WHERE target_id = :targetUserId
                        """)
                .param("targetUserId", targetUserId)
                .query((rs, rowNum) -> new RatingDistributionRow(
                        rs.getInt("r5"), rs.getInt("r4"), rs.getInt("r3"), rs.getInt("r2"), rs.getInt("r1")))
                .single();
    }

    /**
     * Estado de aprovação atual, para {@code PATCH .../approval}: carrega o
     * suficiente para decidir a transição (existência + estado atual) sem
     * puxar o perfil inteiro. Reutilizado também para a releitura pós-CAS
     * falhado (ver {@link #applyApprovalDecision}) — a mesma consulta serve
     * o {@code 404} inicial e o {@code 409} de corrida.
     */
    Optional<ProviderApprovalRow> findApprovalState(UUID providerId) {
        return jdbcClient.sql("""
                        SELECT id, approval_status, approval_reason, approval_decided_by, approval_decided_at
                        FROM provider_profile WHERE id = :id
                        """)
                .param("id", providerId)
                .query(this::mapApprovalRow)
                .optional();
    }

    /**
     * CAS da decisão administrativa (V22; skill {@code admin-moderation-endpoint}
     * §3): só escreve se {@code approval_status} ainda for
     * {@code expectedCurrentStatus} no momento do {@code UPDATE}. Zero linhas
     * afetadas — perdeu a corrida para outra decisão concorrente — devolve
     * {@link Optional#empty()} sem lançar; o chamador releva com
     * {@link #findApprovalState} e decide o {@code 409}.
     *
     * <p>{@code approval_decided_at = now()} no servidor, não
     * {@code Instant.now()} em Java — mesma disciplina de
     * {@code RequestRepository#publish} (V4 {@code published_at}): a âncora
     * temporal da decisão é o instante em que o {@code UPDATE} de facto
     * comita, não o instante em que o processo Java a preparou.
     */
    Optional<ProviderApprovalRow> applyApprovalDecision(
            UUID providerId, String expectedCurrentStatus, String newStatus, String reason, UUID decidedBy) {
        int rows = jdbcClient.sql("""
                        UPDATE provider_profile
                           SET approval_status = :newStatus,
                               approval_reason = :reason,
                               approval_decided_by = :decidedBy,
                               approval_decided_at = now()
                         WHERE id = :id AND approval_status = :expected
                        """)
                .param("newStatus", newStatus)
                .param("reason", reason, java.sql.Types.VARCHAR)
                .param("decidedBy", decidedBy)
                .param("id", providerId)
                .param("expected", expectedCurrentStatus)
                .update();
        return rows > 0 ? findApprovalState(providerId) : Optional.empty();
    }

    private ProviderApprovalRow mapApprovalRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Timestamp decidedAt = rs.getTimestamp("approval_decided_at");
        return new ProviderApprovalRow(
                (UUID) rs.getObject("id"),
                rs.getString("approval_status"),
                rs.getString("approval_reason"),
                (UUID) rs.getObject("approval_decided_by"),
                decidedAt == null ? null : decidedAt.toInstant());
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

    /** {@code headline}/{@code bio} — os únicos campos escalares editáveis por {@code PUT /v1/providers/me} (ver {@code ProvidersService}). */
    void updateEditableFields(UUID providerId, String headline, String bio) {
        jdbcClient.sql("UPDATE provider_profile SET headline = :headline, bio = :bio WHERE id = :id")
                .param("headline", headline, java.sql.Types.VARCHAR)
                .param("bio", bio, java.sql.Types.VARCHAR)
                .param("id", providerId)
                .update();
    }

    /**
     * Substituição total de {@code provider_category} (DELETE + INSERT, na
     * transação do chamador — {@code ProvidersService#updateMyProfile}).
     * Chamador já validou que todos os {@code categoryIds} existem
     * (ver {@code CategoriesApi#findById}); uma violação de FK aqui seria
     * um bug de ordenação da validação, não um caminho esperado.
     */
    void replaceCategories(UUID providerId, Set<UUID> categoryIds) {
        jdbcClient.sql("DELETE FROM provider_category WHERE provider_id = :providerId")
                .param("providerId", providerId)
                .update();
        for (UUID categoryId : categoryIds) {
            jdbcClient.sql("INSERT INTO provider_category (provider_id, category_id) VALUES (:providerId, :categoryId)")
                    .param("providerId", providerId)
                    .param("categoryId", categoryId)
                    .update();
        }
    }

    /**
     * Substituição total das zonas em modo {@code ADMIN_REGION} (DELETE +
     * INSERT). Zonas em modo {@code RADIUS} do mesmo prestador, se existirem,
     * não são tocadas — {@code UpdateProviderProfile} não expõe forma de as
     * definir (ver relatório de entrega); só o modo que o contrato edita é
     * substituído.
     */
    void replaceAdminRegionZones(UUID providerId, Set<String> regionCodes) {
        jdbcClient.sql("DELETE FROM provider_service_area WHERE provider_id = :providerId AND mode = 'ADMIN_REGION'")
                .param("providerId", providerId)
                .update();
        for (String regionCode : regionCodes) {
            jdbcClient.sql("""
                            INSERT INTO provider_service_area (provider_id, mode, region_code)
                            VALUES (:providerId, 'ADMIN_REGION', :regionCode)
                            """)
                    .param("providerId", providerId)
                    .param("regionCode", regionCode)
                    .update();
        }
    }

    /**
     * Substituição total do portefólio (V18): posição = índice na lista
     * enviada pelo cliente. Chamador já confirmou a posse/{@code purpose} de
     * cada {@code imageId} via {@code UploadsApi#confirmOwnedUpload} antes de
     * chegar aqui — ver {@code ProvidersService#updateMyProfile}.
     */
    void replacePortfolio(UUID providerId, List<UUID> imageIds) {
        jdbcClient.sql("DELETE FROM provider_portfolio WHERE provider_id = :providerId")
                .param("providerId", providerId)
                .update();
        int position = 0;
        for (UUID imageId : imageIds) {
            jdbcClient.sql("""
                            INSERT INTO provider_portfolio (provider_id, image_asset_id, position)
                            VALUES (:providerId, :imageId, :position)
                            ON CONFLICT DO NOTHING
                            """)
                    .param("providerId", providerId)
                    .param("imageId", imageId)
                    .param("position", position++)
                    .update();
        }
    }

    private static final String SELECT_BASE = """
            SELECT p.id, p.user_id, p.headline, p.bio, c.name AS company_name, p.verified,
                   p.approval_status, p.rating_avg, p.rating_count,
                   ST_Y(p.location::geometry) AS location_lat, ST_X(p.location::geometry) AS location_lon,
                   p.avatar_asset_id, p.created_at
            FROM provider_profile p
            LEFT JOIN company c ON c.id = p.company_id
            """;

    private ProviderProfileRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal ratingAvg = rs.getBigDecimal("rating_avg");
        Double locationLat = (Double) rs.getObject("location_lat");
        Double locationLon = (Double) rs.getObject("location_lon");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        return new ProviderProfileRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("user_id"),
                rs.getString("headline"),
                rs.getString("bio"),
                rs.getString("company_name"),
                rs.getBoolean("verified"),
                rs.getString("approval_status"),
                ratingAvg == null ? BigDecimal.ZERO : ratingAvg,
                rs.getInt("rating_count"),
                locationLat,
                locationLon,
                (UUID) rs.getObject("avatar_asset_id"),
                createdAt);
    }
}
