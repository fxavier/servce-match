package pt.servimatch.modules.proposals;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado quando um prestador envia (ou reenvia) uma proposta a um
 * pedido (ARQUITETURA §6.3). Sem consumidor nesta onda ({@code notifications}
 * está fora do âmbito); registado no Event Publication Registry na mesma.
 * Ver nota de estrutura em {@link ProposalAccepted} sobre viver no pacote
 * de topo em vez de {@code events/}.
 */
public record ProposalSent(UUID proposalId, UUID requestId, UUID providerId, Instant sentAt) {
}
