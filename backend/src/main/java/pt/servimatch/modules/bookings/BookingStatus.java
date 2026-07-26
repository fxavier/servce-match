package pt.servimatch.modules.bookings;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados de {@code Booking} (ARQUITETURA §13):
 * {@code CONFIRMED → IN_PROGRESS → COMPLETED}, com {@code CANCELLED}/
 * {@code NO_SHOW} alcançáveis a partir dos estados não-terminais.
 *
 * <p>O único endpoint desta onda ({@code completeBooking}) conclui a partir
 * de {@code CONFIRMED} <b>ou</b> {@code IN_PROGRESS} — não existe ainda um
 * endpoint que force a passagem por {@code IN_PROGRESS} (nenhum "iniciar
 * serviço" no contrato v1.0.0), por isso {@code CONFIRMED→COMPLETED} é
 * permitido diretamente. {@code CANCELLED}/{@code NO_SHOW} estão modelados e
 * testados sem endpoint que os acione nesta onda.
 */
public enum BookingStatus {

    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = new EnumMap<>(BookingStatus.class);

    static {
        ALLOWED.put(CONFIRMED, EnumSet.of(IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(COMPLETED, CANCELLED, NO_SHOW));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(NO_SHOW, EnumSet.noneOf(BookingStatus.class));
    }

    public boolean canTransitionTo(BookingStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public void verifyCanTransitionTo(BookingStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Transição ilegal de %s para %s".formatted(this, target));
        }
    }
}
