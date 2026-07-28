package pt.servimatch.modules.uploads.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.uploads.ImageRef;
import pt.servimatch.modules.uploads.UploadPurpose;
import pt.servimatch.modules.uploads.UploadsApi;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository — este bean também não
// precisa de existir antes do primeiro pedido HTTP real ou da primeira
// chamada de outro módulo.
@Service
@Lazy
class UploadsService implements UploadsApi {

    private static final Logger log = LoggerFactory.getLogger(UploadsService.class);

    private final UploadAssetRepository repository;
    private final UploadsProperties properties;
    private final ObjectKeyGenerator objectKeyGenerator;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    UploadsService(UploadAssetRepository repository, UploadsProperties properties,
                   ObjectKeyGenerator objectKeyGenerator, S3Client s3Client, S3Presigner presigner) {
        this.repository = repository;
        this.properties = properties;
        this.objectKeyGenerator = objectKeyGenerator;
        this.s3Client = s3Client;
        this.presigner = presigner;
    }

    // ---------------------------------------------------------------- UploadsApi (público, síncrono)

    @Override
    @Transactional
    public UUID confirmOwnedUpload(UUID imageId, UUID ownerUserId, UploadPurpose purpose) {
        UploadAssetRow row = repository.findById(imageId)
                .orElseThrow(() -> Problems.unprocessable("Imagem " + imageId + " não encontrada."));
        if (!row.ownerUserId().equals(ownerUserId) || !row.purpose().equals(purpose.name())) {
            throw Problems.unprocessable("Imagem " + imageId + " não pertence ao utilizador ou tem finalidade incorreta.");
        }
        if ("CONFIRMED".equals(row.status())) {
            return imageId; // idempotente: já validada, não relê o armazenamento.
        }
        if (!"PENDING".equals(row.status())) {
            throw Problems.unprocessable("Imagem " + imageId + " já não está disponível (status atual: " + row.status() + ").");
        }
        if (row.expiresAt().isBefore(Instant.now())) {
            throw Problems.unprocessable("Imagem " + imageId + " expirou antes de ser confirmada.");
        }

        byte[] header = readHeaderBytes(row.objectKey());
        if (!MagicByteValidator.matches(header, row.contentType())) {
            log.warn("Upload rejeitado: magic bytes não correspondem ao contentType declarado (imageId={}, contentType={})",
                    imageId, row.contentType());
            throw Problems.unprocessable("O ficheiro enviado não corresponde ao tipo declarado (" + row.contentType() + ").");
        }

        boolean confirmed = repository.markConfirmedIfPending(imageId);
        if (!confirmed) {
            // Corrida: outra confirmação concorrente já tratou este imageId entretanto — idempotente.
            log.debug("Confirmação concorrente de upload ignorada (imageId={})", imageId);
        }
        return imageId;
    }

    @Override
    public List<ImageRef> resolve(Collection<UUID> imageIds) {
        return repository.findConfirmedByIds(imageIds).stream()
                .map(row -> new ImageRef(row.id(), presignGet(row.objectKey()), row.contentType()))
                .toList();
    }

    // ---------------------------------------------------------------- Uso pelo controller deste módulo

    UploadTarget createUploadTarget(UUID ownerUserId, UploadPurpose purpose, String contentType, long contentLength) {
        UploadsProperties.PurposeLimit limit = properties.limitFor(purpose);
        if (!limit.allowedContentTypes().contains(contentType)) {
            throw Problems.unsupportedMediaType(
                    "Tipo de conteúdo '" + contentType + "' não suportado para a finalidade " + purpose + ".");
        }
        if (contentLength > limit.maxSizeBytes()) {
            throw Problems.payloadTooLarge(
                    "Ficheiro (" + contentLength + " bytes) acima do limite de " + limit.maxSizeBytes()
                            + " bytes para a finalidade " + purpose + ".");
        }

        UUID imageId = UUID.randomUUID();
        String objectKey = objectKeyGenerator.generate(purpose, contentType);
        Instant expiresAt = Instant.now().plus(properties.pendingRetention());
        repository.insertPending(imageId, ownerUserId, purpose.name(), objectKey, contentType, contentLength, expiresAt);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);

        // TreeMap com comparador insensível a maiúsculas/minúsculas: os
        // cabeçalhos HTTP são case-insensitive (RFC 9110 §5.1), e devolver
        // "Content-Type" e "content-type" como duas chaves distintas (Map
        // comum) faz o cliente enviar o mesmo cabeçalho duas vezes — o que
        // invalida a assinatura SigV4 (o valor efetivamente recebido deixa
        // de ser exatamente o assinado). Aqui garante-se uma única entrada
        // por nome, com o valor assinado a vencer.
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        presigned.signedHeaders().forEach((name, values) -> headers.put(name, String.join(",", values)));
        headers.putIfAbsent("Content-Type", contentType);

        return new UploadTarget(imageId, presigned.url().toString(), headers,
                presigned.expiration() != null ? presigned.expiration() : Instant.now().plus(properties.uploadUrlTtl()),
                limit.maxSizeBytes());
    }

    // ---------------------------------------------------------------- armazenamento

    private byte[] readHeaderBytes(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .range("bytes=0-" + (MagicByteValidator.HEADER_BYTES - 1))
                .build();
        try (ResponseInputStream<GetObjectResponse> in = s3Client.getObject(request)) {
            return in.readNBytes(MagicByteValidator.HEADER_BYTES);
        } catch (NoSuchKeyException e) {
            throw Problems.unprocessable("O ficheiro não foi enviado para o URL emitido (ou já expirou).");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String presignGet(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(properties.downloadUrlTtl())
                .getObjectRequest(getObjectRequest)
                .build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    /** Não usado diretamente por outros módulos — só pelo controlador deste. */
    record UploadTarget(UUID imageId, String uploadUrl, Map<String, String> headers, Instant expiresAt, long maxSizeBytes) {
    }
}
