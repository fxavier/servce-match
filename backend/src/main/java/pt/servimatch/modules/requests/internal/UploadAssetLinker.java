package pt.servimatch.modules.requests.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

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

}
