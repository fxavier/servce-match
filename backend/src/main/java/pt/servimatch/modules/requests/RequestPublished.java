package pt.servimatch.modules.requests;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado quando {@code ServiceRequest} transita {@code DRAFT → PUBLISHED}
 * (ARQUITETURA §4.2, §6.3, §10.3). O módulo {@code matching} (agente
 * {@code backend-matching}, noutra onda paralela) reage de forma assíncrona
 * para selecionar e notificar prestadores elegíveis — este evento carrega
 * apenas os identificadores necessários ao predicado (§10.3): categoria e,
 * quando disponíveis, coordenadas/região do pedido.
 *
 * <p><b>Nota de estrutura:</b> vive no pacote de topo do módulo, não em
 * {@code events/} — ver {@code pt.servimatch.modules.proposals.ProposalAccepted}
 * para a justificação (exposição por omissão do Spring Modulith).
 *
 * <p>Consumidores têm de ser idempotentes (entrega <em>at-least-once</em>,
 * Event Publication Registry).
 */
public record RequestPublished(
        UUID requestId,
        UUID customerId,
        UUID categoryId,
        Double latitude,
        Double longitude,
        String regionCode,
        Instant publishedAt) {
}
