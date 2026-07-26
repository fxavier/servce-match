package pt.servimatch.modules.proposals.internal.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateProposalRequest(
        @NotNull @Valid MoneyDto price,
        @NotNull @Size(max = 2000) String description,
        @Min(0) Integer leadTimeDays,
        Instant validUntil) {
}
