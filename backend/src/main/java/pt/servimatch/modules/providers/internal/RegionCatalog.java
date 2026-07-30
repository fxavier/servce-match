package pt.servimatch.modules.providers.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo <b>estático</b> de regiões administrativas (concelhos) — não é
 * uma tabela. O backend não tem hoje vocabulário controlado de regiões
 * (`ProviderZone.regionCode`/`UpdateProviderProfile.regionCodes` são texto
 * livre no contrato); este catálogo espelha exatamente
 * {@code web/site/src/constants/regions.ts} (mesmos códigos e rótulos) para
 * que a etiqueta apresentada seja igual dos dois lados, e serve dois papéis:
 * <ol>
 *   <li>Resolver {@code ProviderZone.label} a partir do {@code region_code}
 *       guardado em {@code provider_service_area} (V4) — a tabela só tem o
 *       código, nunca o rótulo apresentável.</li>
 *   <li>Validar {@code UpdateProviderProfile.regionCodes} contra um
 *       vocabulário conhecido em {@code PUT /v1/providers/me}, antes de
 *       qualquer escrita — um código desconhecido é {@code 422}, não uma
 *       linha órfã sem rótulo possível.</li>
 * </ol>
 * Se um dia existir um catálogo de regiões no backend (tabela, endpoint
 * {@code GET /v1/regions}), esta classe é substituída por uma leitura real;
 * até lá, mudar um rótulo aqui sem replicar em {@code regions.ts} (ou
 * vice-versa) é o único risco de divergência.
 */
final class RegionCatalog {

    private RegionCatalog() {
    }

    private static final Map<String, String> LABELS_BY_CODE = buildCatalog();

    private static Map<String, String> buildCatalog() {
        Map<String, String> catalog = new LinkedHashMap<>();
        catalog.put("PT-LIS", "Lisboa");
        catalog.put("PT-SNT", "Sintra");
        catalog.put("PT-CSC", "Cascais");
        catalog.put("PT-OEI", "Oeiras");
        catalog.put("PT-LOU", "Loures");
        catalog.put("PT-ALM", "Almada");
        catalog.put("PT-PRT", "Porto");
        catalog.put("PT-VNG", "Vila Nova de Gaia");
        catalog.put("PT-MTS", "Matosinhos");
        catalog.put("PT-BRG", "Braga");
        catalog.put("PT-CBR", "Coimbra");
        catalog.put("PT-FAR", "Faro");
        return Map.copyOf(catalog);
    }

    static boolean isKnown(String regionCode) {
        return regionCode != null && LABELS_BY_CODE.containsKey(regionCode);
    }

    /** Vazio se {@code regionCode} não estiver no catálogo — o chamador decide como tratar essa ausência. */
    static Optional<String> labelFor(String regionCode) {
        return Optional.ofNullable(LABELS_BY_CODE.get(regionCode));
    }
}
