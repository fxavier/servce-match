package pt.servimatch.modules.providers.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Projeção estreita de {@code provider_profile} para a decisão administrativa
 * de aprovação (V22): só as quatro colunas que
 * {@code PATCH /v1/admin/providers/{providerId}/approval} lê e escreve.
 * Separada de {@link ProviderProfileRow} deliberadamente — carregar/mapear o
 * perfil inteiro (portefólio, zonas, categorias) para uma decisão binária
 * seria trabalho e SQL que este endpoint não precisa.
 */
record ProviderApprovalRow(
        UUID id,
        String approvalStatus,
        String approvalReason,
        UUID approvalDecidedBy,
        Instant approvalDecidedAt) {
}
