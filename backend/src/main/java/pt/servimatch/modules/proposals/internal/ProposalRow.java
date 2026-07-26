package pt.servimatch.modules.proposals.internal;

import java.time.Instant;
import java.util.UUID;

record ProposalRow(
        UUID id,
        UUID requestId,
        UUID providerId,
        long priceAmountCents,
        String priceCurrency,
        String description,
        Integer leadTimeDays,
        Instant validUntil,
        String status,
        Instant createdAt) {
}
