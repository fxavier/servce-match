package pt.servimatch.modules.matching;

import java.util.UUID;
import pt.servimatch.modules.geo.GeoPoint;

/**
 * Parâmetros do predicado central de elegibilidade (ADR-0004 §10.3): um
 * prestador cobre a categoria trabalhada e a localização/região indicadas?
 *
 * <p>{@code point} e {@code regionCode} são ambos opcionais, mas pelo menos
 * um deve ser fornecido para que a resposta possa ser {@code true} — sem
 * nenhum dos dois, nenhum {@code provider_service_area} pode corresponder
 * (nem RADIUS nem ADMIN_REGION), pelo que {@link MatchingApi#isEligible}
 * devolve sempre {@code false} nesse caso.
 */
public record EligibilityQuery(UUID providerId, UUID categoryId, GeoPoint point, String regionCode) {

    public EligibilityQuery {
        if (providerId == null) {
            throw new IllegalArgumentException("providerId é obrigatório");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId é obrigatório");
        }
    }
}
