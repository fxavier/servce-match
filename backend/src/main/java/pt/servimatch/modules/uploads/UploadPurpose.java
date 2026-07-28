package pt.servimatch.modules.uploads;

/**
 * Finalidade de um ficheiro carregado (espelha
 * {@code docs/api/openapi.yaml#/components/schemas/UploadPurpose}).
 * Determina, no servidor, os tipos de conteúdo aceites e o tamanho máximo
 * ({@code servimatch.uploads.purposes.*}) — nunca decidido pelo cliente.
 */
public enum UploadPurpose {
    REQUEST_ATTACHMENT,
    MESSAGE_ATTACHMENT,
    PROVIDER_PORTFOLIO,
    PROVIDER_DOCUMENT,
    AVATAR
}
