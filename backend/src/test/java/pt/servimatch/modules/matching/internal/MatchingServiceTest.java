package pt.servimatch.modules.matching.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.servimatch.modules.geo.GeoPoint;
import pt.servimatch.modules.matching.EligibilityQuery;

/**
 * Cobre a lógica de curto-circuito de {@link MatchingService} — a parte que
 * não precisa de PostGIS real para ser provada (não repete os casos
 * geoespaciais de fronteira, que exigem Testcontainers; ver relatório da
 * tarefa).
 */
@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private EligibilityRepository repository;

    @Test
    void isEligibleReturnsFalseWithoutQueryingWhenNeitherPointNorRegionGiven() {
        MatchingService service = new MatchingService(repository);
        UUID providerId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        boolean eligible = service.isEligible(new EligibilityQuery(providerId, categoryId, null, null));

        assertThat(eligible).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    void isEligibleDelegatesToRepositoryWhenPointGiven() {
        MatchingService service = new MatchingService(repository);
        UUID providerId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        GeoPoint point = new GeoPoint(38.72, -9.14);
        when(repository.isEligible(providerId, categoryId, point, null)).thenReturn(true);

        boolean eligible = service.isEligible(new EligibilityQuery(providerId, categoryId, point, null));

        assertThat(eligible).isTrue();
    }

    @Test
    void findEligibleProviderIdsReturnsEmptySetWithoutQueryingWhenNoGeoFilter() {
        MatchingService service = new MatchingService(repository);

        Set<UUID> ids = service.findEligibleProviderIds(UUID.randomUUID(), null, null);

        assertThat(ids).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findEligibleProviderIdsRejectsNullCategory() {
        MatchingService service = new MatchingService(repository);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.findEligibleProviderIds(null, new GeoPoint(38.72, -9.14), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filterEligibleRequestIdsReturnsEmptySetWithoutQueryingWhenCandidatesEmpty() {
        MatchingService service = new MatchingService(repository);

        Set<UUID> ids = service.filterEligibleRequestIds(UUID.randomUUID(), Set.of());

        assertThat(ids).isEmpty();
        verify(repository, never()).filterEligibleRequestIds(any(), any());
    }

    @Test
    void filterEligibleRequestIdsDelegatesToRepositoryForNonEmptyCandidates() {
        MatchingService service = new MatchingService(repository);
        UUID providerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(repository.filterEligibleRequestIds(eq(providerId), any())).thenReturn(Set.of(requestId));

        Set<UUID> ids = service.filterEligibleRequestIds(providerId, Set.of(requestId));

        assertThat(ids).containsExactly(requestId);
    }
}
