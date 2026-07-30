package pt.servimatch.modules.providers.internal.web;

import java.util.UUID;

/** Espelha {@code components.schemas.ProviderSummary}. */
public record ProviderSummaryDto(
        UUID id,
        String displayName,
        String headline,
        String companyName,
        double ratingAvg,
        int ratingCount,
        boolean verified,
        boolean premiumBadge,
        String avatarUrl) {
}
