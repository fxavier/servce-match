package pt.servimatch.modules.uploads.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pt.servimatch.modules.uploads.UploadPurpose;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Configuração do módulo {@code uploads} — object storage compatível S3
 * (ARQUITETURA §14: Amazon S3/Cloudflare R2 em produção, MinIO em
 * desenvolvimento) e limites por {@code purpose}, tudo por configuração
 * (nunca constantes espalhadas pelo código, CLAUDE.md/ARQUITETURA §9).
 *
 * @param endpoint         URL do endpoint S3-compatível. Vazio/omisso em produção
 *                         para usar a resolução por região da AWS; preenchido em
 *                         desenvolvimento para apontar ao MinIO local.
 * @param region           região assinada nos pedidos (MinIO ignora o valor, mas o SDK exige um).
 * @param bucket           bucket privado único de uploads.
 * @param accessKey        credencial estática (dev/MinIO). Em produção, preferir a cadeia
 *                         de credenciais por omissão (IAM) deixando isto vazio.
 * @param secretKey        ver {@code accessKey}.
 * @param pathStyleAccess  {@code true} para MinIO (endereçamento por caminho); {@code false}
 *                         para S3/R2 reais (endereçamento por subdomínio virtual).
 * @param uploadUrlTtl     validade do URL pré-assinado de escrita (PUT) devolvido por {@code createUpload}.
 * @param downloadUrlTtl   validade dos URLs de leitura (GET) devolvidos por {@code UploadsApi#resolve}.
 * @param pendingRetention janela em que um {@code upload_asset PENDING} pode ainda ser confirmado;
 *                         passado este prazo sem nunca ter sido referenciado, é recolhido (§11.2).
 * @param purposes         limites de tamanho/tipos aceites por {@link UploadPurpose}.
 */
@ConfigurationProperties(prefix = "servimatch.uploads")
public record UploadsProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl,
        Duration pendingRetention,
        Map<UploadPurpose, PurposeLimit> purposes
) {

    public UploadsProperties {
        if (region == null || region.isBlank()) {
            region = "eu-west-1";
        }
        if (uploadUrlTtl == null) {
            uploadUrlTtl = Duration.ofMinutes(15);
        }
        if (downloadUrlTtl == null) {
            downloadUrlTtl = Duration.ofHours(1);
        }
        if (pendingRetention == null) {
            pendingRetention = Duration.ofDays(1);
        }
        purposes = purposes == null ? Map.of() : new EnumMap<>(purposes);
    }

    /** Limite aplicado para o {@code purpose}, ou o mais restritivo por omissão se não configurado. */
    PurposeLimit limitFor(UploadPurpose purpose) {
        PurposeLimit configured = purposes.get(purpose);
        return configured != null ? configured : PurposeLimit.DEFAULT;
    }

    public record PurposeLimit(long maxSizeBytes, List<String> allowedContentTypes) {

        static final PurposeLimit DEFAULT = new PurposeLimit(5L * 1024 * 1024, List.of("image/jpeg", "image/png"));

        public PurposeLimit {
            allowedContentTypes = allowedContentTypes == null ? List.of() : List.copyOf(allowedContentTypes);
        }
    }
}
