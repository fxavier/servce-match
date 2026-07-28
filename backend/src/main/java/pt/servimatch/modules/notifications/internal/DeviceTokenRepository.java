package pt.servimatch.modules.notifications.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class DeviceTokenRepository {

    private final JdbcClient jdbcClient;

    DeviceTokenRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Registo/atualização idempotente por {@code token} (contrato,
     * {@code UNIQUE (token)} da V5): um dispositivo já conhecido só
     * atualiza {@code user_id}/{@code platform}/{@code app_version}/
     * {@code last_seen_at} — nunca duplica nem lança conflito de chave
     * (cobre também o caso de o mesmo aparelho mudar de conta, comum em
     * telemóveis partilhados/demo).
     */
    void upsert(UUID id, UUID userId, String token, String platform, String appVersion) {
        jdbcClient.sql("""
                        INSERT INTO device_token (id, user_id, token, platform, app_version, last_seen_at)
                        VALUES (:id, :userId, :token, :platform, :appVersion, now())
                        ON CONFLICT (token) DO UPDATE SET
                            user_id = EXCLUDED.user_id,
                            platform = EXCLUDED.platform,
                            app_version = EXCLUDED.app_version,
                            last_seen_at = now()
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("token", token)
                .param("platform", platform)
                .param("appVersion", appVersion)
                .update();
    }

    /** @return número de linhas apagadas (0 ou 1) — nunca lança por token inexistente/alheio; o chamador decide. */
    int deleteByTokenAndOwner(String token, UUID userId) {
        return jdbcClient.sql("DELETE FROM device_token WHERE token = :token AND user_id = :userId")
                .param("token", token)
                .param("userId", userId)
                .update();
    }

    List<DeviceTokenRow> findByUserId(UUID userId) {
        return jdbcClient.sql("""
                        SELECT id, user_id, token, platform, app_version, last_seen_at, created_at
                        FROM device_token WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query(DeviceTokenRepository::map)
                .list();
    }

    private static DeviceTokenRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DeviceTokenRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("user_id"),
                rs.getString("token"),
                rs.getString("platform"),
                rs.getString("app_version"),
                rs.getTimestamp("last_seen_at").toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
