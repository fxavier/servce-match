/**
 * Matching — predicado de elegibilidade prestador↔pedido: subscrição
 * ativa, cobertura de zona e categoria trabalhada. Reage de forma
 * assíncrona a {@code RequestPublished} ({@code @ApplicationModuleListener}).
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-matching}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §10.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Matching"
)
package pt.servimatch.modules.matching;
