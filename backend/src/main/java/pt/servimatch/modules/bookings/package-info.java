/**
 * Bookings — agenda/marcações resultantes de uma proposta aceite
 * ({@code CONFIRMED → IN_PROGRESS → COMPLETED}, ou {@code CANCELLED} /
 * {@code NO_SHOW}). {@code BookingCompleted} habilita a avaliação.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-domain}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §13.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Bookings"
)
package pt.servimatch.modules.bookings;
