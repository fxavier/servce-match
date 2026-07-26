package pt.servimatch.modules.proposals;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static pt.servimatch.modules.proposals.ProposalStatus.ACCEPTED;
import static pt.servimatch.modules.proposals.ProposalStatus.CANCELLED;
import static pt.servimatch.modules.proposals.ProposalStatus.EXPIRED;
import static pt.servimatch.modules.proposals.ProposalStatus.REJECTED;
import static pt.servimatch.modules.proposals.ProposalStatus.SENT;
import static pt.servimatch.modules.proposals.ProposalStatus.SUPERSEDED;

/**
 * Máquina de estados de {@code Proposal} (ARQUITETURA §4.4). Caminho feliz
 * (aceitação) + todas as saídas alternativas de {@code SENT} + transição
 * ilegal a partir de cada estado terminal (CLAUDE.md).
 */
class ProposalStatusTest {

    @Test
    void happyPathSentToAccepted() {
        assertThat(SENT.canTransitionTo(ACCEPTED)).isTrue();
    }

    @Test
    void sentCanReachEveryOtherOutcome() {
        assertThat(SENT.canTransitionTo(REJECTED)).isTrue();
        assertThat(SENT.canTransitionTo(CANCELLED)).isTrue();
        assertThat(SENT.canTransitionTo(EXPIRED)).isTrue();
        assertThat(SENT.canTransitionTo(SUPERSEDED)).isTrue();
    }

    @Test
    void terminalStatesRejectAnyTransition() {
        for (ProposalStatus terminal : new ProposalStatus[]{ACCEPTED, REJECTED, CANCELLED, EXPIRED, SUPERSEDED}) {
            for (ProposalStatus target : ProposalStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).as("%s -> %s", terminal, target).isFalse();
            }
        }
    }

    @Test
    void acceptedRejectsTransitioningToSuperseded() {
        assertThatIllegalStateException().isThrownBy(() -> ACCEPTED.verifyCanTransitionTo(SUPERSEDED));
    }
}
