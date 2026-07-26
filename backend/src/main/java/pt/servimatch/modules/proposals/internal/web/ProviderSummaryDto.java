package pt.servimatch.modules.proposals.internal.web;

import java.util.UUID;

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
