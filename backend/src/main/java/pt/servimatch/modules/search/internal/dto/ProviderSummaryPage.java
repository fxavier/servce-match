package pt.servimatch.modules.search.internal.dto;

import java.util.List;

/** Espelha {@code components.schemas.ProviderSummaryPage} de {@code docs/api/openapi.yaml}. */
public record ProviderSummaryPage(List<ProviderSummary> items, PageMeta page) {
}
