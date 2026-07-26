package pt.servimatch.modules.requests.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Confirma {@code upload_asset} (V6) e cria o vínculo {@code request_image}
 * quando um {@code imageId} é referenciado em {@code CreateServiceRequest}.
 *
 * <p><b>Compromisso deliberado e temporário</b> (mesma natureza que
 * {@link CategoryLookup}): não existe ainda um módulo/API pública
 * {@code uploads} (é do {@code backend-platform}, fora do âmbito deste
 * agente) que exponha "confirmar imageId". Este ficheiro faz a leitura e
 * escrita mínimas necessárias — existência, dono, {@code purpose} — e
 * marca {@code CONFIRMED}; a verificação por <em>magic bytes</em>
 * (CLAUDE.md §4) continua a não ser feita aqui e tem de ficar no módulo
 * {@code uploads} quando existir (ver relatório de entrega, pedido
 * explícito ao {@code backend-platform}).
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

    /** @return true se o asset existe, pertence a {@code ownerUserId} e tem o purpose esperado. */
    boolean confirmOwnedPending(UUID imageId, UUID ownerUserId, String expectedPurpose) {
        int rows = jdbcClient.sql("""
                        UPDATE upload_asset
                        SET status = 'CONFIRMED', confirmed_at = now()
                        WHERE id = :id AND owner_user_id = :ownerId AND purpose = :purpose
                          AND status IN ('PENDING', 'CONFIRMED')
                        """)
                .param("id", imageId)
                .param("ownerId", ownerUserId)
                .param("purpose", expectedPurpose)
                .update();
        return rows > 0;
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

    List<RequestImageRow> findByRequestId(UUID requestId) {
        return jdbcClient.sql("""
                        SELECT ri.image_asset_id, ua.object_key, ua.content_type, ri.position
                        FROM request_image ri
                        JOIN upload_asset ua ON ua.id = ri.image_asset_id
                        WHERE ri.request_id = :requestId
                        ORDER BY ri.position
                        """)
                .param("requestId", requestId)
                .query((rs, rowNum) -> new RequestImageRow(
                        (UUID) rs.getObject("image_asset_id"),
                        rs.getString("object_key"),
                        rs.getString("content_type"),
                        rs.getInt("position")))
                .list();
    }

}
