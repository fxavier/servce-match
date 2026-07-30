/**
 * Reviews — avaliações verificadas: só quem teve um {@code Booking} em
 * estado {@code COMPLETED} pode avaliar. Dono da tabela {@code review}, por
 * isso também implementa {@code GET /v1/providers/{providerId}/reviews}
 * (tag "Providers & Search" no contrato, mesmo padrão de
 * {@code requests.listProviderInbox} — o dono da tabela implementa o
 * endpoint, independentemente da tag do OpenAPI).
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform}. Implementação
 * pertence ao agente {@code backend-domain} — ver {@code docs/AGENTES.md} e
 * {@code docs/ARQUITETURA.md} §4.6, §13.
 *
 * <p>{@code allowedDependencies} (revisão "dados-reais", Onda 1,
 * {@code backend-platform}):
 * <ul>
 *   <li>{@code bookings} — {@code BookingsApi}/{@code BookingStatus}, para
 *       verificar {@code COMPLETED} antes de aceitar {@code POST
 *       /v1/reviews}.</li>
 *   <li>{@code users} — {@code UsersApi}, e para calcular
 *       {@code ReviewWithAuthor.authorName} como PII reduzida (primeiro
 *       nome + inicial do apelido) a partir de
 *       {@code users.display_name} — o endpoint público nunca expõe o
 *       nome completo (ver descrição de {@code listProviderReviews} no
 *       contrato).</li>
 *   <li>{@code providers} — <b>nova nesta revisão.</b>
 *       {@code ProvidersApi}, para {@code GET
 *       /v1/providers/{providerId}/reviews} devolver {@code 404} quando o
 *       prestador não existe, sem duplicar a leitura de
 *       {@code provider_profile} por SQL direto (ADR-0010 só autoriza
 *       leitura entre módulos para consultas <em>set-based</em>; esta é
 *       uma leitura pontual — a API Java chega).</li>
 * </ul>
 *
 * <p><b>Nunca {@code reviews → providers} torna-se
 * {@code providers → reviews}, nem {@code reviews → bookings} torna-se
 * {@code bookings → reviews}.</b> Qualquer uma das duas direções inversas
 * fecha um ciclo com a dependência já declarada aqui —
 * {@code ApplicationModules.verify()} rejeita-o. Um pedido de
 * {@code providers} ou {@code bookings} para lerem algo de {@code reviews}
 * (ex.: {@code rating_avg}/{@code rating_count} agregados — achado ainda
 * não resolvido do ADR-0011 D9) não se resolve abrindo essa dependência;
 * escalar ao {@code arquiteto}.
 *
 * <p>Um módulo que precise de uma dependência nova pede-a a este agente.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Reviews",
        allowedDependencies = {"modules.bookings", "modules.users", "modules.providers"}
)
package pt.servimatch.modules.reviews;
