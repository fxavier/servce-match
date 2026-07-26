package pt.servimatch.modules.matching.internal;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pt.servimatch.modules.geo.GeoPoint;
import pt.servimatch.modules.matching.EligibilityQuery;
import pt.servimatch.modules.matching.MatchingApi;

@Service
class MatchingService implements MatchingApi {

    private final EligibilityRepository repository;

    MatchingService(EligibilityRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isEligible(EligibilityQuery query) {
        if (query.point() == null && query.regionCode() == null) {
            // Nenhum modo de cobertura pode corresponder sem ponto nem região.
            return false;
        }
        return repository.isEligible(query.providerId(), query.categoryId(), query.point(), query.regionCode());
    }

    @Override
    public Set<UUID> findEligibleProviderIds(UUID categoryId, GeoPoint point, String regionCode) {
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId é obrigatório");
        }
        if (point == null && regionCode == null) {
            return Set.of();
        }
        return repository.findEligibleProviderIds(categoryId, point, regionCode);
    }

    @Override
    public Set<UUID> filterEligibleRequestIds(UUID providerId, Set<UUID> candidateRequestIds) {
        if (providerId == null) {
            throw new IllegalArgumentException("providerId é obrigatório");
        }
        if (candidateRequestIds == null || candidateRequestIds.isEmpty()) {
            return Set.of();
        }
        return repository.filterEligibleRequestIds(providerId, List.copyOf(candidateRequestIds));
    }
}
