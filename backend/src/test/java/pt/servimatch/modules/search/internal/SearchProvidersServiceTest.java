package pt.servimatch.modules.search.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.servimatch.modules.geo.GeoPoint;
import pt.servimatch.modules.search.internal.dto.ProviderSummary;
import pt.servimatch.modules.search.internal.dto.ProviderSummaryPage;

@ExtendWith(MockitoExtension.class)
class SearchProvidersServiceTest {

    @Mock
    private ProviderSearchRepository repository;

    @Test
    void happyPathBuildsEnvelopeWithoutNextCursorWhenLastPage() {
        SearchProvidersService service = new SearchProvidersService(repository);
        ProviderSummary summary = new ProviderSummary(
                UUID.randomUUID(), "Ana Canalizadora", "Canalizações", null, 4.5f, 12, true, false, null);
        when(repository.search(isNull(), any(), isNull(), isNull(), eq(21), eq(0)))
                .thenReturn(List.of(summary));

        ProviderSummaryPage page = service.search(null, null, null, null, null, null, null);

        assertThat(page.items()).containsExactly(summary);
        assertThat(page.page().nextCursor()).isNull();
    }

    @Test
    void happyPathReturnsNextCursorWhenMoreRowsThanLimit() {
        SearchProvidersService service = new SearchProvidersService(repository);
        List<ProviderSummary> twoRows = List.of(
                new ProviderSummary(UUID.randomUUID(), "A", null, null, 5f, 1, false, false, null),
                new ProviderSummary(UUID.randomUUID(), "B", null, null, 4f, 1, false, false, null));
        when(repository.search(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(twoRows);

        ProviderSummaryPage page = service.search(null, null, null, null, null, "1", null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.page().nextCursor()).isNotNull();
        assertThat(SearchCursor.decode(page.page().nextCursor())).isEqualTo(1);
    }

    @Test
    void latWithoutLonIsRejected() {
        SearchProvidersService service = new SearchProvidersService(repository);

        assertThatThrownBy(() -> service.search(null, "38.72", null, null, null, null, null))
                .isInstanceOf(InvalidSearchParametersException.class);
    }

    @Test
    void resolvesGeoPointWhenBothCoordinatesGiven() {
        SearchProvidersService service = new SearchProvidersService(repository);
        when(repository.search(isNull(), eq(new GeoPoint(38.72, -9.14)), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.search(null, "38.72", "-9.14", null, null, null, null);

        verify(repository).search(isNull(), eq(new GeoPoint(38.72, -9.14)), isNull(), isNull(), eq(21), eq(0));
    }

    @Test
    void invalidLimitOutOfRangeIsRejected() {
        SearchProvidersService service = new SearchProvidersService(repository);

        assertThatThrownBy(() -> service.search(null, null, null, null, null, "0", null))
                .isInstanceOf(InvalidSearchParametersException.class);
        assertThatThrownBy(() -> service.search(null, null, null, null, null, "101", null))
                .isInstanceOf(InvalidSearchParametersException.class);
    }

    @Test
    void invalidCategoryIdIsRejected() {
        SearchProvidersService service = new SearchProvidersService(repository);

        assertThatThrownBy(() -> service.search("not-a-uuid", null, null, null, null, null, null))
                .isInstanceOf(InvalidSearchParametersException.class);
    }
}
