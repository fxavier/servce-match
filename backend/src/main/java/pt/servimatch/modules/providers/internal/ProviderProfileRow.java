package pt.servimatch.modules.providers.internal;

import java.math.BigDecimal;
import java.util.UUID;

record ProviderProfileRow(
        UUID id,
        UUID userId,
        String headline,
        String companyName,
        boolean verified,
        String approvalStatus,
        String visibilityState,
        BigDecimal ratingAvg,
        int ratingCount) {
}
