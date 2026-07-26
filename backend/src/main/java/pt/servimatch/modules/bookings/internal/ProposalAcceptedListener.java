package pt.servimatch.modules.bookings.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.servimatch.modules.proposals.ProposalAccepted;

/**
 * Cria a {@code Booking} quando uma proposta é aceite. Reage por evento em
 * vez de {@code proposals} chamar {@code BookingsApi} sincronamente — essa
 * direção síncrona criaria um ciclo de módulos, porque {@code bookings} já
 * depende de {@code proposals} para resolver os participantes de uma
 * marcação (ver {@code BookingsService.resolveParticipants} e o relatório
 * de entrega). {@code createFromProposal} é idempotente (índice único
 * {@code uq_booking_proposal_id}, V10), como exige a entrega
 * <em>at-least-once</em> do {@code @ApplicationModuleListener}.
 *
 * <p>{@code @Lazy}: ver nota em {@code UserRepository}; o Spring regista o
 * método {@code @EventListener}-family por introspeção do tipo (sem
 * instanciar o bean) e só resolve a instância real quando um evento
 * {@code ProposalAccepted} é efetivamente publicado — a laziness não afeta
 * a entrega de eventos, só adia a criação do bean até ao primeiro uso.
 */
@Component
@Lazy
class ProposalAcceptedListener {

    private static final Logger log = LoggerFactory.getLogger(ProposalAcceptedListener.class);

    private final BookingsService bookingsService;

    ProposalAcceptedListener(BookingsService bookingsService) {
        this.bookingsService = bookingsService;
    }

    @ApplicationModuleListener
    void on(ProposalAccepted event) {
        bookingsService.createFromProposal(event.proposalId());
        log.info("Booking garantida para proposalId={} (correlação por evento, não por PII)", event.proposalId());
    }
}
