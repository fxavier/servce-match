package pt.servimatch.modules.bookings.internal.web;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /v1/bookings/{bookingId}} — estende {@link BookingDto} com o
 * {@code users.id} da contraparte: {@code targetId} em {@code POST
 * /v1/reviews} é um {@code users.id}, mas {@code Proposal.providerId} é um
 * {@code provider_profile.id} — sem {@code counterpartUserId} o cliente não
 * consegue preencher {@code targetId} a partir desta resposta (defeito
 * assimétrico, invisível em teste manual do lado do prestador; ver
 * {@code docs/api/openapi.yaml#/components/schemas/BookingDetail}).
 */
public record BookingDetailDto(
        UUID id,
        UUID proposalId,
        Instant scheduledStart,
        Instant scheduledEnd,
        String status,
        Instant completedAt,
        String requestTitle,
        UUID counterpartUserId,
        String counterpartName,
        boolean canReview) {
}
