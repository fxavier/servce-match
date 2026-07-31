package pt.servimatch.modules.providers;

/**
 * Valores atribuíveis pela decisão administrativa sobre
 * {@code provider_profile.approval_status} ({@code UpdateProviderApproval},
 * openapi.yaml:800). Deliberadamente um tipo Java distinto de
 * {@link ProviderApprovalStatus} — que tem 4 valores, este só 3 — porque
 * {@code PENDING} nunca é destino de uma decisão administrativa
 * (ADR-0011 D7; skill {@code admin-moderation-endpoint} §2). Um único enum
 * partilhado reabriria o caminho para "aprovar" um prestador de volta a
 * {@code PENDING}.
 *
 * <p>{@link #targetStatus()} é a única tradução para
 * {@link ProviderApprovalStatus} — nomeada e coincidente por desenho (os
 * três valores desta enumeração têm o mesmo nome do estado que produzem),
 * para que a máquina de transições em {@link ProviderApprovalStatus} não
 * tenha de manter uma segunda tabela de tradução.
 */
public enum ProviderApprovalDecision {

    APPROVED,
    REJECTED,
    SUSPENDED;

    public ProviderApprovalStatus targetStatus() {
        return ProviderApprovalStatus.valueOf(name());
    }
}
