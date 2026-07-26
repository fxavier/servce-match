package pt.servimatch.modules.billing;

/**
 * Ciclo de vida da subscrição (ARQUITETURA §12.1, ADR-0007).
 *
 * <pre>
 * (checkout) ──▶ PENDING ──pagamento ok──▶ ACTIVE ──fim do período sem renovar──▶ EXPIRED
 *                   │                         │
 *                   └──pagamento falha──▶ PAST_DUE ──retries/janela esgotam──▶ CANCELLED
 *                                            │
 *                                            └──pagamento ok──▶ ACTIVE
 *        ACTIVE ──cancelar (cancel_at_period_end)──▶ fim do período ──▶ CANCELLED
 * </pre>
 *
 * <p>{@code EXPIRED} e {@code CANCELLED} são estados terminais: nenhuma
 * transição os abandona. Um evento de pagamento tardio referente a uma
 * subscrição já terminal nunca a ressuscita — ver
 * {@code SubscriptionLifecycle}.
 */
public enum SubscriptionStatus {
    PENDING,
    ACTIVE,
    PAST_DUE,
    EXPIRED,
    CANCELLED;

    /** Estados que contam como "subscrição a pagar" para efeitos de visibilidade (grace em PAST_DUE). */
    public boolean grantsVisibility() {
        return this == ACTIVE || this == PAST_DUE;
    }

    public boolean isTerminal() {
        return this == EXPIRED || this == CANCELLED;
    }
}
