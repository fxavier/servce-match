package pt.servimatch.modules.proposals.internal.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MoneyDto(
        @NotNull @Min(0) Long amountCents,
        @NotNull @Size(min = 3, max = 3) String currency) {
}
