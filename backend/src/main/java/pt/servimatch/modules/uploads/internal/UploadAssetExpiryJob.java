package pt.servimatch.modules.uploads.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Recolhe {@code imageId} nunca referenciados (ARQUITETURA §11.2, comentário
 * V6: "job de recolha... varre PENDING com expires_at vencido"). Não apaga o
 * objeto em armazenamento aqui — se o cliente nunca chegou a fazer o
 * {@code PUT}, não há nada para apagar; se fez mas nunca confirmou, o objeto
 * órfão fica para uma política de ciclo de vida do próprio bucket (fora do
 * âmbito deste job, que só marca o registo em base de dados).
 */
@Component
class UploadAssetExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(UploadAssetExpiryJob.class);
    private static final int BATCH_SIZE = 500;

    private final UploadAssetRepository repository;

    UploadAssetExpiryJob(UploadAssetRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${servimatch.uploads.expiry-cron:0 30 3 * * *}")
    void collectExpiredPending() {
        List<UUID> expired = repository.findExpiredPendingIds(Instant.now(), BATCH_SIZE);
        if (expired.isEmpty()) {
            return;
        }
        int marked = repository.markExpired(expired);
        log.info("Upload assets recolhidos por expiração sem confirmação: {}", marked);
    }
}
