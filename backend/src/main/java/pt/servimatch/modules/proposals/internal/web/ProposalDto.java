package pt.servimatch.modules.proposals.internal.web;

import java.time.Instant;
import java.util.UUID;

public record ProposalDto(
        UUID id,
        UUID requestId,
        UUID providerId,
        ProviderSummaryDto providerSummary,
        MoneyDto price,
        String description,
        Integer leadTimeDays,
        Instant validUntil,
        String status,
        Instant createdAt) {
}
