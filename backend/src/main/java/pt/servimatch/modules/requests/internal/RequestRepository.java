package pt.servimatch.modules.requests.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code service_request} (V7).
 *
 * <p>{@code @Lazy}: ver nota equivalente em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@Repository
@Lazy
class RequestRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, customer_id, category_id, title, description,
                   address_line1, address_line2, address_postal_code, address_city,
                   address_region_code, address_country,
                   ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon,
                   urgency, availability, status, published_at, created_at
            FROM service_request
            """;

    private final JdbcClient jdbcClient;

    RequestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    record NewRequest(
            UUID customerId,
            UUID categoryId,
            String title,
            String description,
            String line1,
            String line2,
            String postalCode,
            String city,
            String regionCode,
            String country,
            Double lat,
            Double lon,
            String urgency,
            String availability) {
    }

    UUID insertDraft(NewRequest r) {
        return jdbcClient.sql("""
                        INSERT INTO service_request (
                            customer_id, category_id, title, description,
                            address_line1, address_line2, address_postal_code, address_city,
                            address_region_code, address_country, location, urgency, availability
                        ) VALUES (
                            :customerId, :categoryId, :title, :description,
                            :line1, :line2, :postalCode, :city, :regionCode, :country,
                            CASE WHEN :lat IS NULL OR :lon IS NULL THEN NULL
                                 ELSE ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography END,
                            :urgency, :availability
                        )
                        RETURNING id
                        """)
                .param("customerId", r.customerId())
                .param("categoryId", r.categoryId())
                .param("title", r.title())
                .param("description", r.description(), Types.VARCHAR)
                .param("line1", r.line1(), Types.VARCHAR)
                .param("line2", r.line2(), Types.VARCHAR)
                .param("postalCode", r.postalCode(), Types.VARCHAR)
                .param("city", r.city(), Types.VARCHAR)
                .param("regionCode", r.regionCode(), Types.VARCHAR)
                .param("country", r.country())
                .param("lat", r.lat(), Types.DOUBLE)
                .param("lon", r.lon(), Types.DOUBLE)
                .param("urgency", r.urgency())
                .param("availability", r.availability(), Types.VARCHAR)
                .query(UUID.class)
                .single();
    }

    Optional<ServiceRequestRow> findById(UUID id) {
        return jdbcClient.sql(SELECT_COLUMNS + " WHERE id = :id")
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    /** CAS {@code DRAFT → PUBLISHED}. @return a linha atualizada, vazio se o pedido não estava em DRAFT. */
    Optional<ServiceRequestRow> publish(UUID id) {
        int rows = jdbcClient.sql("""
                        UPDATE service_request SET status = 'PUBLISHED', published_at = now()
                        WHERE id = :id AND status = 'DRAFT'
                        """)
                .param("id", id)
                .update();
        return rows > 0 ? findById(id) : Optional.empty();
    }

    /** CAS {@code PUBLISHED → IN_NEGOTIATION}. @return true se a transição ocorreu nesta chamada. */
    boolean markInNegotiation(UUID id) {
        int rows = jdbcClient.sql("UPDATE service_request SET status = 'IN_NEGOTIATION' WHERE id = :id AND status = 'PUBLISHED'")
                .param("id", id)
                .update();
        return rows > 0;
    }

    /** CAS {@code IN_NEGOTIATION → CONFIRMED}. @return true se a transição ocorreu nesta chamada. */
    boolean confirm(UUID id) {
        int rows = jdbcClient.sql("UPDATE service_request SET status = 'CONFIRMED' WHERE id = :id AND status = 'IN_NEGOTIATION'")
                .param("id", id)
                .update();
        return rows > 0;
    }

    /**
     * Página ordenada por {@code created_at DESC, id DESC} (listProviderInbox).
     * {@code categoryIds} restringe aos pedidos das categorias trabalhadas
     * pelo prestador (pré-filtro exigido por
     * {@code MatchingApi#filterEligibleRequestIds}, que só cobre geografia —
     * ver {@code RequestsService.listInbox}). Nunca chamado com
     * {@code categoryIds} vazio: o chamador devolve a página vazia sem
     * consultar a base de dados nesse caso.
     */
    java.util.List<ServiceRequestRow> findPage(java.util.List<String> statuses, java.util.List<UUID> categoryIds,
                                                CursorCodec.Position after, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS)
                .append(" WHERE status IN (:statuses) AND category_id IN (:categoryIds) ");
        if (after != null) {
            sql.append(" AND (created_at, id) < (:afterCreatedAt, :afterId) ");
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit ");
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString())
                .param("statuses", statuses)
                .param("categoryIds", categoryIds)
                .param("limit", limit);
        if (after != null) {
            spec = spec.param("afterCreatedAt", java.sql.Timestamp.from(after.createdAt()))
                    .param("afterId", after.id());
        }
        return spec.query(this::mapRow).list();
    }

    /**
     * Página ordenada por {@code created_at DESC, id DESC} (listMyRequests,
     * {@code GET /v1/requests}). Filtro de dono <b>sempre em SQL</b>
     * ({@code customer_id = :customerId}), nunca em memória (isolamento
     * entre clientes — CLAUDE.md); {@code status} é um único valor já
     * validado contra {@link pt.servimatch.modules.requests.ServiceRequestStatus}
     * pelo chamador (um filtro inválido é 400 antes de chegar aqui, nunca
     * uma cláusula que silenciosamente não bate com nada).
     */
    java.util.List<ServiceRequestRow> findPageForCustomer(UUID customerId, String status,
                                                            CursorCodec.Position after, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(" WHERE customer_id = :customerId ");
        if (status != null) {
            sql.append(" AND status = :status ");
        }
        if (after != null) {
            sql.append(" AND (created_at, id) < (:afterCreatedAt, :afterId) ");
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit ");
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString())
                .param("customerId", customerId)
                .param("limit", limit);
        if (status != null) {
            spec = spec.param("status", status);
        }
        if (after != null) {
            spec = spec.param("afterCreatedAt", java.sql.Timestamp.from(after.createdAt()))
                    .param("afterId", after.id());
        }
        return spec.query(this::mapRow).list();
    }

    /**
     * Títulos em lote para {@link pt.servimatch.modules.requests.RequestsApi#findTitlesByIds}
     * — uma única consulta {@code WHERE id IN (:ids)}, nunca uma por id.
     */
    java.util.Map<UUID, String> findTitlesByIds(java.util.Collection<UUID> requestIds) {
        return jdbcClient.sql("SELECT id, title FROM service_request WHERE id IN (:ids)")
                .param("ids", requestIds)
                .query((rs, rowNum) -> java.util.Map.entry((UUID) rs.getObject("id"), rs.getString("title")))
                .list()
                .stream()
                .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
    }

    private ServiceRequestRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Double lat = (Double) rs.getObject("lat");
        Double lon = (Double) rs.getObject("lon");
        java.sql.Timestamp publishedAt = rs.getTimestamp("published_at");
        return new ServiceRequestRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("customer_id"),
                (UUID) rs.getObject("category_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("address_line1"),
                rs.getString("address_line2"),
                rs.getString("address_postal_code"),
                rs.getString("address_city"),
                rs.getString("address_region_code"),
                rs.getString("address_country"),
                lat,
                lon,
                rs.getString("urgency"),
                rs.getString("availability"),
                rs.getString("status"),
                publishedAt == null ? null : publishedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
