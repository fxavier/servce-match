package pt.servimatch.modules.search.internal.dto;

import java.util.UUID;

/** Espelha {@code components.schemas.ProviderSummary} de {@code docs/api/openapi.yaml}. */
public record ProviderSummary(
        UUID id,
        String displayName,
        String headline,
        String companyName,
        float ratingAvg,
        int ratingCount,
        boolean verified,
        boolean premiumBadge,
        String avatarUrl) {
}
