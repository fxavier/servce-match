/**
 * Reviews — avaliações verificadas: só quem teve um {@code Booking} em
 * estado {@code COMPLETED} pode avaliar.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-domain}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §4.6, §13.
 *
 * <p>{@code allowedDependencies} fechado nesta revisão (Onda 1b,
 * {@code backend-platform}), a partir dos imports reais: {@code bookings}
 * ({@code BookingsApi}/{@code BookingStatus}, para verificar
 * {@code COMPLETED}) e {@code users} ({@code UsersApi}). Um módulo que
 * precise de uma dependência nova pede-a a este agente.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Reviews",
        allowedDependencies = {"modules.bookings", "modules.users"}
)
package pt.servimatch.modules.reviews;
