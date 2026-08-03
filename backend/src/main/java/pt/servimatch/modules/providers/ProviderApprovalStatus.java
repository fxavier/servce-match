package pt.servimatch.modules.providers;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados de {@code provider_profile.approval_status} (V4, V22;
 * ADR-0011 D7):
 *
 * <pre>
 * PENDING ──ADMIN aprova───▶ APPROVED ──ADMIN suspende──▶ SUSPENDED
 *   │
 *   └──ADMIN rejeita───────▶ REJECTED
 * </pre>
 *
 * <p>{@code PENDING} é sempre o estado inicial (provisionamento JIT,
 * {@code ProvidersApi#ensureProvisioned}) e nunca um destino — só
 * {@link ProviderApprovalDecision} (3 valores, sem {@code PENDING}) pode
 * aparecer do lado direito de {@link #canTransitionTo}. {@code REJECTED} e
 * {@code SUSPENDED} são terminais: o contrato
 * ({@code PATCH /v1/admin/providers/{providerId}/approval}) não define
 * nenhuma transição de saída para nenhum dos dois.
 *
 * <p>Transição não permitida é erro de domínio traduzido para {@code 409}
 * pelo serviço ({@code ProvidersService#decideApproval}), nunca uma exceção
 * de runtime genérica — mesma disciplina das outras máquinas de estado do
 * backend ({@code ServiceRequestStatus}, {@code ProposalStatus},
 * {@code BookingStatus}).
 */
public enum ProviderApprovalStatus {

    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    private static final Map<ProviderApprovalStatus, Set<ProviderApprovalDecision>> ALLOWED =
            new EnumMap<>(ProviderApprovalStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(ProviderApprovalDecision.APPROVED, ProviderApprovalDecision.REJECTED));
        ALLOWED.put(APPROVED, EnumSet.of(ProviderApprovalDecision.SUSPENDED));
        ALLOWED.put(REJECTED, EnumSet.noneOf(ProviderApprovalDecision.class));
        ALLOWED.put(SUSPENDED, EnumSet.noneOf(ProviderApprovalDecision.class));
    }

    public boolean canTransitionTo(ProviderApprovalDecision decision) {
        return ALLOWED.get(this).contains(decision);
    }
}
