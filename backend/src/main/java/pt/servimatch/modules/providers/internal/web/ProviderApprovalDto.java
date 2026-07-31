package pt.servimatch.modules.providers.internal.web;

import pt.servimatch.modules.providers.ProviderApprovalStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Espelha {@code components.schemas.ProviderApproval}. Resposta de
 * {@code PATCH /v1/admin/providers/{providerId}/approval}.
 *
 * <p>{@code decidedBy}/{@code decidedAt} são sempre não nulos aqui: esta
 * DTO só é construída a partir de uma decisão que acabou de acontecer
 * nesta chamada (ver {@code ProvidersService#decideApproval}), nunca a
 * partir de uma leitura posterior — {@code approval_decided_by} pode
 * legitimamente tornar-se {@code NULL} mais tarde (FK {@code ON DELETE SET
 * NULL} para o administrador, V22), mas esse caso não passa por este DTO,
 * que não é usado por nenhum {@code GET}.
 */
public record ProviderApprovalDto(
        UUID providerId,
        ProviderApprovalStatus approvalStatus,
        String reason,
        UUID decidedBy,
        Instant decidedAt) {
}
