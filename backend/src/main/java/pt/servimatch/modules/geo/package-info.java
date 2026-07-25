/**
 * Geo — geocodificação de moradas (Nominatim, assíncrona e com cache) e
 * modelos de cobertura geográfica do prestador (raio via PostGIS
 * {@code ST_DWithin}, ou regiões administrativas). Ver ADR-0004.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-matching}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §10.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Geo"
)
package pt.servimatch.modules.geo;
