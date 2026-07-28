package pt.servimatch.modules.uploads.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class UploadAssetRepository {

    private final JdbcClient jdbcClient;

    UploadAssetRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void insertPending(UUID id, UUID ownerUserId, String purpose, String objectKey,
                        String contentType, long contentLength, Instant expiresAt) {
        jdbcClient.sql("""
                        INSERT INTO upload_asset
                            (id, owner_user_id, purpose, object_key, content_type, content_length, status, expires_at)
                        VALUES (:id, :ownerUserId, :purpose, :objectKey, :contentType, :contentLength, 'PENDING', :expiresAt)
                        """)
                .param("id", id)
                .param("ownerUserId", ownerUserId)
                .param("purpose", purpose)
                .param("objectKey", objectKey)
                .param("contentType", contentType)
                .param("contentLength", contentLength)
                .param("expiresAt", Timestamp.from(expiresAt))
                .update();
    }

    Optional<UploadAssetRow> findById(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, owner_user_id, purpose, object_key, content_type, content_length, status, expires_at
                        FROM upload_asset WHERE id = :id
                        """)
                .param("id", id)
                .query(UploadAssetRepository::map)
                .optional();
    }

    List<UploadAssetRow> findConfirmedByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT id, owner_user_id, purpose, object_key, content_type, content_length, status, expires_at
                        FROM upload_asset WHERE status = 'CONFIRMED' AND id IN (:ids)
                        """)
                .param("ids", ids)
                .query(UploadAssetRepository::map)
                .list();
    }

    /** CAS: só confirma se ainda {@code PENDING}. {@code false} se já não estava {@code PENDING} (ex.: corrida). */
    boolean markConfirmedIfPending(UUID id) {
        int rows = jdbcClient.sql("""
                        UPDATE upload_asset SET status = 'CONFIRMED', confirmed_at = now()
                        WHERE id = :id AND status = 'PENDING'
                        """)
                .param("id", id)
                .update();
        return rows > 0;
    }

    List<UUID> findExpiredPendingIds(Instant now, int limit) {
        return jdbcClient.sql("""
                        SELECT id FROM upload_asset
                        WHERE status = 'PENDING' AND expires_at < :now
                        ORDER BY expires_at
                        LIMIT :limit
                        """)
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .query((rs, rowNum) -> (UUID) rs.getObject("id"))
                .list();
    }

    int markExpired(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return jdbcClient.sql("""
                        UPDATE upload_asset SET status = 'EXPIRED'
                        WHERE id IN (:ids) AND status = 'PENDING'
                        """)
                .param("ids", ids)
                .update();
    }

    private static UploadAssetRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UploadAssetRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("owner_user_id"),
                rs.getString("purpose"),
                rs.getString("object_key"),
                rs.getString("content_type"),
                rs.getLong("content_length"),
                rs.getString("status"),
                rs.getTimestamp("expires_at").toInstant());
    }
}
