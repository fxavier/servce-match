package pt.servimatch.modules.providers.internal.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Espelha {@code components.schemas.UpdateProviderProfile}. Corpo de
 * {@code PUT /v1/providers/me} — substituição <b>total</b> das três listas
 * (ver {@code ProvidersService#updateMyProfile}). Deliberadamente sem
 * {@code verified}, {@code approvalStatus} nem {@code ratingAvg}: o contrato
 * não os expõe neste corpo — não há nada para "ignorar" explicitamente
 * porque não chegam a ser um campo aceite (ver relatório de entrega).
 */
public record UpdateProviderProfileRequest(
        @NotNull @Size(max = 160) String headline,
        @Size(max = 4000) String bio,
        @NotNull List<UUID> categoryIds,
        @NotNull List<String> regionCodes,
        @NotNull List<UUID> portfolioImageIds) {
}
