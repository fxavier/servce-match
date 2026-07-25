/**
 * Reviews — avaliações verificadas: só quem teve um {@code Booking} em
 * estado {@code COMPLETED} pode avaliar.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-domain}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §4.6, §13.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Reviews"
)
package pt.servimatch.modules.reviews;
