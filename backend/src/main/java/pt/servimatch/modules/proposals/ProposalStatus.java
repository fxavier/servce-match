package pt.servimatch.modules.proposals;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados de {@code Proposal} (ARQUITETURA §4.4):
 *
 * <pre>
 * SENT ──cliente aceita──▶ ACCEPTED
 *   │
 *   ├──prestador cancela──▶ CANCELLED
 *   ├──cliente rejeita────▶ REJECTED
 *   └──validade expira────▶ EXPIRED
 * Quando o pedido é CONFIRMED por outra proposta: SENT ──▶ SUPERSEDED
 * </pre>
 *
 * <p>Nesta onda, os endpoints acionam apenas {@code SENT→ACCEPTED}
 * (acceptProposal, na proposta aceite), {@code SENT→SUPERSEDED} (nas
 * concorrentes, mesma transação) e {@code SENT→CANCELLED} (reenvio de
 * proposta pelo mesmo prestador ao mesmo pedido, ARQUITETURA §4.4 — "reenviar
 * substitui a anterior"). {@code REJECTED}/{@code EXPIRED} não têm endpoint
 * nesta onda; a máquina está completa e testada mesmo assim.
 */
public enum ProposalStatus {

    SENT,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    SUPERSEDED;

    private static final Map<ProposalStatus, Set<ProposalStatus>> ALLOWED = new EnumMap<>(ProposalStatus.class);

    static {
        ALLOWED.put(SENT, EnumSet.of(ACCEPTED, REJECTED, CANCELLED, EXPIRED, SUPERSEDED));
        ALLOWED.put(ACCEPTED, EnumSet.noneOf(ProposalStatus.class));
        ALLOWED.put(REJECTED, EnumSet.noneOf(ProposalStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(ProposalStatus.class));
        ALLOWED.put(EXPIRED, EnumSet.noneOf(ProposalStatus.class));
        ALLOWED.put(SUPERSEDED, EnumSet.noneOf(ProposalStatus.class));
    }

    public boolean canTransitionTo(ProposalStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public void verifyCanTransitionTo(ProposalStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Transição ilegal de %s para %s".formatted(this, target));
        }
    }
}
