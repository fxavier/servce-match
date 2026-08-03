package pt.servimatch.modules.providers.internal.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pt.servimatch.modules.providers.ProviderApprovalDecision;

/**
 * Espelha {@code components.schemas.UpdateProviderApproval}. Corpo de
 * {@code PATCH /v1/admin/providers/{providerId}/approval}.
 *
 * <p>{@code decision} é o tipo enumerado {@link ProviderApprovalDecision}
 * (3 valores, sem {@code PENDING}) — não uma {@code String} crua: um valor
 * fora do enum falha a desserialização Jackson e o
 * {@code GlobalExceptionHandler} devolve {@code 400}, sem chegar à
 * validação de negócio (mesma classe de defeito que a skill
 * {@code admin-moderation-endpoint} associa ao {@code statusFilter} de
 * {@code listInbox} — nunca aceitar a string crua).
 *
 * <p>{@code reason} é opcional aqui a nível de schema: obrigatório apenas
 * para {@code REJECTED}/{@code SUSPENDED} é regra de negócio
 * ({@code 422} se faltar, ver {@code ProvidersService#decideApproval}), não
 * de validação de bean — o schema não sabe, sem repetir a máquina de
 * estados, para que decisão o motivo é exigido.
 */
public record UpdateProviderApprovalRequest(
        @NotNull ProviderApprovalDecision decision,
        @Size(max = 2000) String reason) {
}
