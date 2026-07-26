package pt.servimatch.modules.payments.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/** Espelha {@code CreateSubscription} no contrato. */
public record CreateSubscriptionRequest(
        @NotNull UUID planId,
        @NotBlank @Pattern(regexp = "stripe|eupago|ifthenpay|paypal") String gateway,
        String returnUrl) {
}
