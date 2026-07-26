package pt.servimatch.modules.requests;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static pt.servimatch.modules.requests.ServiceRequestStatus.CANCELLED;
import static pt.servimatch.modules.requests.ServiceRequestStatus.COMPLETED;
import static pt.servimatch.modules.requests.ServiceRequestStatus.CONFIRMED;
import static pt.servimatch.modules.requests.ServiceRequestStatus.DRAFT;
import static pt.servimatch.modules.requests.ServiceRequestStatus.IN_NEGOTIATION;
import static pt.servimatch.modules.requests.ServiceRequestStatus.IN_PROGRESS;
import static pt.servimatch.modules.requests.ServiceRequestStatus.PUBLISHED;

/**
 * Máquina de estados de {@code ServiceRequest} (ARQUITETURA §4.3). Caminho
 * feliz completo + pelo menos uma transição ilegal por estado não-terminal
 * (CLAUDE.md).
 */
class ServiceRequestStatusTest {

    @Test
    void happyPathTraversesEveryState() {
        assertThat(DRAFT.canTransitionTo(PUBLISHED)).isTrue();
        assertThat(PUBLISHED.canTransitionTo(IN_NEGOTIATION)).isTrue();
        assertThat(IN_NEGOTIATION.canTransitionTo(CONFIRMED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(IN_PROGRESS)).isTrue();
        assertThat(IN_PROGRESS.canTransitionTo(COMPLETED)).isTrue();
    }

    @Test
    void cancelledIsReachableFromEveryNonTerminalStateExceptConfirmedOnwards() {
        assertThat(DRAFT.canTransitionTo(CANCELLED)).isTrue();
        assertThat(PUBLISHED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(IN_NEGOTIATION.canTransitionTo(CANCELLED)).isTrue();
    }

    @Test
    void draftRejectsSkippingDirectlyToConfirmed() {
        assertThat(DRAFT.canTransitionTo(CONFIRMED)).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> DRAFT.verifyCanTransitionTo(CONFIRMED));
    }

    @Test
    void publishedRejectsSkippingNegotiation() {
        assertThat(PUBLISHED.canTransitionTo(CONFIRMED)).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> PUBLISHED.verifyCanTransitionTo(CONFIRMED));
    }

    @Test
    void inNegotiationRejectsGoingBackToPublished() {
        assertThat(IN_NEGOTIATION.canTransitionTo(PUBLISHED)).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> IN_NEGOTIATION.verifyCanTransitionTo(PUBLISHED));
    }

    @Test
    void confirmedCannotBeCancelledAnymore() {
        assertThat(CONFIRMED.canTransitionTo(CANCELLED)).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> CONFIRMED.verifyCanTransitionTo(CANCELLED));
    }

    @Test
    void inProgressRejectsGoingBackToConfirmed() {
        assertThat(IN_PROGRESS.canTransitionTo(CONFIRMED)).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> IN_PROGRESS.verifyCanTransitionTo(CONFIRMED));
    }

    @Test
    void completedAndCancelledAreTerminal() {
        for (ServiceRequestStatus target : ServiceRequestStatus.values()) {
            assertThat(COMPLETED.canTransitionTo(target)).isFalse();
            assertThat(CANCELLED.canTransitionTo(target)).isFalse();
        }
    }
}
