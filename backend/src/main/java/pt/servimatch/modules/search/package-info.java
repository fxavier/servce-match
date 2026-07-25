/**
 * Search — pesquisa de prestadores e categorias sobre PostgreSQL FTS
 * ({@code tsvector} + GIN, {@code pg_trgm} para pesquisa aproximada).
 * Ver ADR-0005; só migra para um motor de pesquisa dedicado sob pressão
 * real de escala/relevância.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-matching}
 * — ver {@code docs/AGENTES.md}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Search"
)
package pt.servimatch.modules.search;
