package pt.servimatch.modules.bookings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static pt.servimatch.modules.bookings.BookingStatus.CANCELLED;
import static pt.servimatch.modules.bookings.BookingStatus.COMPLETED;
import static pt.servimatch.modules.bookings.BookingStatus.CONFIRMED;
import static pt.servimatch.modules.bookings.BookingStatus.IN_PROGRESS;
import static pt.servimatch.modules.bookings.BookingStatus.NO_SHOW;

/**
 * Máquina de estados de {@code Booking} (ARQUITETURA §13). Caminho feliz
 * (conclusão a partir de {@code CONFIRMED} e de {@code IN_PROGRESS}) +
 * transição ilegal por estado (CLAUDE.md).
 */
class BookingStatusTest {

    @Test
    void happyPathConfirmedToCompleted() {
        assertThat(CONFIRMED.canTransitionTo(COMPLETED)).isTrue();
    }

    @Test
    void happyPathInProgressToCompleted() {
        assertThat(IN_PROGRESS.canTransitionTo(COMPLETED)).isTrue();
    }

    @Test
    void confirmedCanBeCancelledOrNoShow() {
        assertThat(CONFIRMED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(NO_SHOW)).isTrue();
    }

    @Test
    void terminalStatesRejectAnyTransition() {
        for (BookingStatus terminal : new BookingStatus[]{COMPLETED, CANCELLED, NO_SHOW}) {
            for (BookingStatus target : BookingStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).as("%s -> %s", terminal, target).isFalse();
            }
        }
    }

    @Test
    void completedRejectsGoingBackToInProgress() {
        assertThatIllegalStateException().isThrownBy(() -> COMPLETED.verifyCanTransitionTo(IN_PROGRESS));
    }
}
