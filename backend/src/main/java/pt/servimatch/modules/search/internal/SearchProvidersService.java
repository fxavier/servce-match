package pt.servimatch.modules.search.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pt.servimatch.modules.geo.GeoPoint;
import pt.servimatch.modules.search.internal.dto.PageMeta;
import pt.servimatch.modules.search.internal.dto.ProviderSummary;
import pt.servimatch.modules.search.internal.dto.ProviderSummaryPage;

@Service
class SearchProvidersService {

    static final int DEFAULT_LIMIT = 20;
    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 100;

    private final ProviderSearchRepository repository;

    SearchProvidersService(ProviderSearchRepository repository) {
        this.repository = repository;
    }

    ProviderSummaryPage search(
            String categoryIdRaw, String latRaw, String lonRaw, String regionCode,
            String q, String limitRaw, String cursorRaw) {

        UUID categoryId = parseCategoryId(categoryIdRaw);
        GeoPoint point = parsePoint(latRaw, lonRaw);
        int limit = parseLimit(limitRaw);
        int offset = cursorRaw == null || cursorRaw.isBlank() ? 0 : SearchCursor.decode(cursorRaw);
        String normalizedRegionCode = blankToNull(regionCode);
        String normalizedQ = blankToNull(q);

        // Pede limit+1 para saber, sem uma segunda consulta, se há próxima página.
        List<ProviderSummary> rows = repository.search(
                categoryId, point, normalizedRegionCode, normalizedQ, limit + 1, offset);

        boolean hasMore = rows.size() > limit;
        List<ProviderSummary> items = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? SearchCursor.encode(offset + limit) : null;

        return new ProviderSummaryPage(items, new PageMeta(nextCursor));
    }

    private static UUID parseCategoryId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new InvalidSearchParametersException("categoryId", "categoryId não é um UUID válido.");
        }
    }

    private static GeoPoint parsePoint(String latRaw, String lonRaw) {
        boolean hasLat = latRaw != null && !latRaw.isBlank();
        boolean hasLon = lonRaw != null && !lonRaw.isBlank();
        if (!hasLat && !hasLon) {
            return null;
        }
        if (hasLat != hasLon) {
            throw new InvalidSearchParametersException("lat/lon", "lat e lon têm de ser fornecidos em conjunto.");
        }
        try {
            return new GeoPoint(Double.parseDouble(latRaw), Double.parseDouble(lonRaw));
        } catch (NumberFormatException ex) {
            throw new InvalidSearchParametersException("lat/lon", "lat/lon têm de ser números.");
        } catch (IllegalArgumentException ex) {
            throw new InvalidSearchParametersException("lat/lon", ex.getMessage());
        }
    }

    private static int parseLimit(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LIMIT;
        }
        int limit;
        try {
            limit = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new InvalidSearchParametersException("limit", "limit tem de ser um número inteiro.");
        }
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new InvalidSearchParametersException(
                    "limit", "limit tem de estar entre %d e %d.".formatted(MIN_LIMIT, MAX_LIMIT));
        }
        return limit;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
