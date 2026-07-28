package pt.servimatch.modules.notifications.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// @Lazy: ver nota em pt.servimatch.modules.users.internal.UserRepository —
// este bean também não precisa de existir antes do primeiro pedido HTTP real.
@Service
@Lazy
class DeviceTokenService {

    private final DeviceTokenRepository repository;

    DeviceTokenService(DeviceTokenRepository repository) {
        this.repository = repository;
    }

    /** Idempotente por {@code token} (contrato) — ver {@link DeviceTokenRepository#upsert}. */
    @Transactional
    void register(UUID userId, String token, DevicePlatform platform, String appVersion) {
        repository.upsert(UUID.randomUUID(), userId, token, platform.name(), appVersion);
    }

    /**
     * Remove {@code token}, mas só se pertencer a {@code userId} — um
     * pedido de remoção do token de outro utilizador é indistinguível, na
     * resposta, de um token inexistente: ambos {@code 404}, sem confirmar
     * nem negar a existência do token alheio.
     */
    @Transactional
    void delete(UUID userId, String token) {
        int rows = repository.deleteByTokenAndOwner(token, userId);
        if (rows == 0) {
            throw Problems.notFound("Token de dispositivo não encontrado.");
        }
    }
}
