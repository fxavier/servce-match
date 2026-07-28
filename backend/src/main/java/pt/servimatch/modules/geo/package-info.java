/**
 * Geo — geocodificação de moradas (Nominatim, assíncrona e com cache) e
 * modelos de cobertura geográfica do prestador (raio via PostGIS
 * {@code ST_DWithin}, ou regiões administrativas). Ver ADR-0004.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-matching}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §10.
 *
 * <p>Módulo-folha: {@code allowedDependencies} vazio nesta revisão (Onda
 * 1b, {@code backend-platform}) — sem imports de outro módulo hoje;
 * consumido por {@code matching} e {@code search}, nunca o inverso.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Geo",
        allowedDependencies = {}
)
package pt.servimatch.modules.geo;
