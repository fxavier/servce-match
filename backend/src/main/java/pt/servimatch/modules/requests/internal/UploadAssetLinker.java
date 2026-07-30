package pt.servimatch.modules.requests.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vínculo {@code request_image} (V7) entre um pedido e os {@code imageId}
 * (já confirmados via {@code UploadsApi.confirmOwnedUpload} pelo chamador,
 * {@link RequestsService#createDraft}) que o cliente referenciou em
 * {@code CreateServiceRequest}.
 *
 * <p>Só guarda o par {@code (request_id, image_asset_id, position)} — nunca
 * {@code object_key} nem qualquer outro dado de {@code upload_asset}, tabela
 * que este módulo já não lê nem escreve (ADR-0010, fechado nesta onda: ver
 * relatório de entrega). A resolução para URLs de leitura assinadas é feita
 * por {@code UploadsApi#resolve} em {@link RequestsService#toDto}.
 *
 * <p>{@code @Lazy}: ver nota equivalente em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@Component
@Lazy
class UploadAssetLinker {

    private final JdbcClient jdbcClient;

    UploadAssetLinker(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void linkToRequest(UUID requestId, List<UUID> imageIds) {
        int position = 0;
        for (UUID imageId : imageIds) {
            jdbcClient.sql("""
                            INSERT INTO request_image (request_id, image_asset_id, position)
                            VALUES (:requestId, :imageId, :position)
                            ON CONFLICT DO NOTHING
                            """)
                    .param("requestId", requestId)
                    .param("imageId", imageId)
                    .param("position", position++)
                    .update();
        }
    }

    /** Ordem de posição; {@code imageAssetId} é depois resolvido via {@code UploadsApi#resolve}. */
    List<RequestImageRow> findByRequestId(UUID requestId) {
        return jdbcClient.sql("""
                        SELECT image_asset_id, position
                        FROM request_image
                        WHERE request_id = :requestId
                        ORDER BY position
                        """)
                .param("requestId", requestId)
                .query((rs, rowNum) -> new RequestImageRow(
                        (UUID) rs.getObject("image_asset_id"),
                        rs.getInt("position")))
                .list();
    }

    /**
     * Variante em lote de {@link #findByRequestId(UUID)} para páginas
     * inteiras ({@code RequestsService#toDtoPage}): uma única consulta para
     * todos os {@code requestId} da página, nunca uma por pedido (CLAUDE.md
     * — "imagens... da página inteira em uma query cada"). Devolve mapa
     * agrupado por {@code request_id}; pedidos sem imagens ficam ausentes do
     * mapa (o chamador usa {@code getOrDefault(id, List.of())}).
     */
    Map<UUID, List<RequestImageRow>> findByRequestIds(Collection<UUID> requestIds) {
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        record Linked(UUID requestId, UUID imageAssetId, int position) {
        }
        List<Linked> rows = jdbcClient.sql("""
                        SELECT request_id, image_asset_id, position
                        FROM request_image
                        WHERE request_id IN (:requestIds)
                        ORDER BY request_id, position
                        """)
                .param("requestIds", requestIds)
                .query((rs, rowNum) -> new Linked(
                        (UUID) rs.getObject("request_id"),
                        (UUID) rs.getObject("image_asset_id"),
                        rs.getInt("position")))
                .list();
        return rows.stream().collect(Collectors.groupingBy(
                Linked::requestId,
                Collectors.mapping(l -> new RequestImageRow(l.imageAssetId(), l.position()), Collectors.toList())));
    }

}
