package pt.servimatch.modules.uploads;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * API pública do módulo {@code uploads} — a única forma correta de um
 * módulo de domínio (ex. {@code requests}, {@code chat}, {@code providers})
 * lidar com um {@code imageId} recebido de um cliente. Nenhum outro módulo
 * deve ler a tabela {@code upload_asset} diretamente nem construir URLs de
 * objeto à mão.
 *
 * <p>Fluxo esperado de um módulo consumidor, ao aceitar {@code imageIds} /
 * {@code attachmentIds} num pedido de criação/edição de recurso:
 * <ol>
 *   <li>Para cada {@code imageId} recebido, chamar
 *       {@link #confirmOwnedUpload(UUID, UUID, UploadPurpose)}. Lança
 *       {@link org.springframework.web.ErrorResponseException} (422) se o
 *       {@code imageId} não existir, não pertencer a {@code ownerUserId},
 *       tiver o {@code purpose} errado, tiver expirado, ou — só na primeira
 *       confirmação — se os <em>magic bytes</em> do objeto em armazenamento
 *       não corresponderem ao {@code contentType} declarado (CLAUDE.md §4).
 *       O módulo consumidor <b>não</b> precisa de tratar este erro: deixá-lo
 *       propagar até ao {@code GlobalExceptionHandler} já produz a resposta
 *       RFC 9457 correta.</li>
 *   <li>Persistir apenas o {@code imageId} (chave estrangeira para
 *       {@code upload_asset.id}) na sua própria tabela de associação — nunca
 *       o {@code object_key} nem qualquer URL.</li>
 *   <li>Ao construir a resposta, chamar {@link #resolve(Collection)} com os
 *       {@code imageId} associados para obter URLs de leitura assinados e
 *       frescos (o URL emitido em {@code createUpload} é de escrita, de uso
 *       único, e não deve ser reutilizado para leitura).</li>
 * </ol>
 *
 * <p>Confirmar é <b>idempotente</b>: repetir a chamada para um
 * {@code imageId} já {@code CONFIRMED} pelo mesmo dono/{@code purpose} não
 * volta a ler o armazenamento, só valida ownership.
 */
public interface UploadsApi {

    /**
     * Confirma que {@code imageId} existe, pertence a {@code ownerUserId},
     * tem o {@code purpose} esperado, e (na primeira confirmação) que o
     * ficheiro em armazenamento passa a validação por <em>magic bytes</em>
     * para o {@code contentType} declarado no pedido de upload original.
     *
     * @return o próprio {@code imageId}, para encadeamento.
     * @throws org.springframework.web.ErrorResponseException 422 em qualquer falha de validação acima.
     */
    UUID confirmOwnedUpload(UUID imageId, UUID ownerUserId, UploadPurpose purpose);

    /**
     * Resolve URLs de leitura assinados e com expiração para um conjunto de
     * {@code imageId} já confirmados. {@code imageId} inexistentes, não
     * {@code CONFIRMED}, ou desconhecidos são omitidos do resultado (não
     * lançam erro) — o chamador decide como lidar com uma contagem menor que
     * a pedida.
     */
    List<ImageRef> resolve(Collection<UUID> imageIds);
}
