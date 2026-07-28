/**
 * Bookings — agenda/marcações resultantes de uma proposta aceite
 * ({@code CONFIRMED → IN_PROGRESS → COMPLETED}, ou {@code CANCELLED} /
 * {@code NO_SHOW}). {@code BookingCompleted} habilita a avaliação.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-domain}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §13.
 *
 * <p>{@code allowedDependencies} fechado nesta revisão (Onda 1b,
 * {@code backend-platform}), a partir dos imports reais: {@code proposals}
 * ({@code ProposalsApi} e o evento {@code ProposalAccepted}, consumido por
 * {@code @ApplicationModuleListener} — ver {@code ProposalAcceptedListener}),
 * {@code requests} ({@code RequestsApi}), {@code providers}
 * ({@code ProvidersApi}) e {@code users} ({@code UsersApi}). Um módulo que
 * precise de uma dependência nova pede-a a este agente.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Bookings",
        allowedDependencies = {"modules.proposals", "modules.requests", "modules.providers", "modules.users"}
)
package pt.servimatch.modules.bookings;
