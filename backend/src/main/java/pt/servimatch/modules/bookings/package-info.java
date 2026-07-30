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
 * ({@code ProvidersApi}) e {@code users} ({@code UsersApi}). Inalterado na
 * revisão "dados-reais" (Onda 1): {@code GET /v1/bookings/{bookingId}}
 * (detalhe, ainda por implementar) não precisa de nenhuma dependência nova
 * — é uma leitura da própria tabela {@code booking} com autorização por
 * participante ({@code customerId}/{@code providerId}, já presentes na
 * linha).
 *
 * <p><b>Nunca {@code bookings → reviews}.</b> {@code reviews} já depende de
 * {@code bookings} (para verificar {@code BookingStatus.COMPLETED} antes de
 * aceitar uma avaliação — ver {@code reviews.package-info}); a direção
 * inversa fecharia um ciclo. Se o ecrã de avaliação precisar de saber "esta
 * marcação já foi avaliada", a resposta vem de {@code reviews} para quem
 * pergunta, nunca de {@code bookings} a perguntar a {@code reviews}.
 *
 * <p>Um módulo que precise de uma dependência nova pede-a a este agente.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Bookings",
        allowedDependencies = {"modules.proposals", "modules.requests", "modules.providers", "modules.users"}
)
package pt.servimatch.modules.bookings;
