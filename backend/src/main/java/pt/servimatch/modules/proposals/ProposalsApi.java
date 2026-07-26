package pt.servimatch.modules.proposals;

import java.util.Optional;
import java.util.UUID;

/** API pública do módulo {@code proposals}, usada pelo módulo {@code bookings}. */
public interface ProposalsApi {

    Optional<ProposalView> findById(UUID proposalId);

    record ProposalView(UUID id, UUID requestId, UUID providerId, ProposalStatus status) {
    }
}
