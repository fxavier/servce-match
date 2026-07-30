/**
 * Search — pesquisa de prestadores e categorias sobre PostgreSQL FTS
 * ({@code tsvector} + GIN, {@code pg_trgm} para pesquisa aproximada).
 * Ver ADR-0005; só migra para um motor de pesquisa dedicado sob pressão
 * real de escala/relevância.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-matching}
 * — ver {@code docs/AGENTES.md}.
 *
 * <p>{@code allowedDependencies} fechado nesta revisão (Onda 1b,
 * {@code backend-platform}), a partir dos imports reais: {@code geo}
 * (filtro geográfico de {@code GET /v1/search/providers}) e {@code billing}
 * — compor a elegibilidade do prestador a partir do estado da subscrição,
 * em vez de copiar o predicado. Um módulo que precise de uma dependência
 * nova pede-a a este agente.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Search",
        allowedDependencies = {"modules.geo", "modules.billing"}
)
package pt.servimatch.modules.search;
