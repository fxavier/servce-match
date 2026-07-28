/**
 * Matching — predicado de elegibilidade prestador↔pedido: subscrição
 * ativa, cobertura de zona e categoria trabalhada. Reage de forma
 * assíncrona a {@code RequestPublished} ({@code @ApplicationModuleListener}).
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-matching}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §10.
 *
 * <p>{@code allowedDependencies} fechado nesta revisão (Onda 1b,
 * {@code backend-platform}), a partir dos imports reais: só {@code geo}
 * (predicado de cobertura, {@code GeoPoint}/{@code CoverageSql}). Nota: o
 * {@code @ApplicationModuleListener} de {@code RequestPublished} descrito
 * acima ainda não existe no código (só {@code isEligible}/
 * {@code findEligibleProviderIds}/{@code filterEligibleRequestIds}
 * síncronos) — quando existir, {@code requests} entra aqui. Um módulo que
 * precise de uma dependência nova pede-a a este agente.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Matching",
        allowedDependencies = {"modules.geo"}
)
package pt.servimatch.modules.matching;
