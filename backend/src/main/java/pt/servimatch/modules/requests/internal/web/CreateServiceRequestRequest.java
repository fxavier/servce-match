package pt.servimatch.modules.requests.internal.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pt.servimatch.modules.requests.UrgencyLevel;

import java.util.List;
import java.util.UUID;

/**
 * Espelha {@code components.schemas.CreateServiceRequest}
 * ({@code openapi.yaml:1082-1095}). {@code urgency} é opcional a nível de
 * schema (ausente do {@code required}) — {@code null} significa "usar o
 * omisso", resolvido em {@code RequestsService#createDraft} para
 * {@link UrgencyLevel#NORMAL}, nunca aqui.
 *
 * <p>{@code availability} tem {@code @Size} alinhado com
 * {@code service_request.availability VARCHAR(500)} (V7): sem limite aqui, o
 * bean validation deixava passar um valor que só a base de dados rejeitava
 * (achado M5 da auditoria — mesma classe de defeito que motivou o enum
 * {@link UrgencyLevel}, ver o seu javadoc).
 */
public record CreateServiceRequestRequest(
        @NotNull UUID categoryId,
        @NotNull @Size(min = 3, max = 140) String title,
        @Size(max = 4000) String description,
        @NotNull @Valid AddressDto address,
        UrgencyLevel urgency,
        @Size(max = 500) String availability,
        List<UUID> imageIds) {
}
