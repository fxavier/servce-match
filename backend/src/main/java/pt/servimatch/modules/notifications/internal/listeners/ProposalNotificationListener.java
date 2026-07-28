package pt.servimatch.modules.notifications.internal.listeners;

import org.springframework.context.annotation.Lazy;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.servimatch.modules.notifications.internal.NotificationDispatcher;
import pt.servimatch.modules.proposals.ProposalAccepted;

import java.util.Map;

/**
 * Notifica cliente e prestador quando uma proposta é aceite
 * ({@code ProposalAccepted}, publicado por {@code proposals}, agente
 * {@code backend-domain} — ver o javadoc do próprio evento: "o módulo
 * {@code notifications} despoleta a notificação de confirmação a ambas as
 * partes"). Reage por evento — nunca {@code proposals} chamando este módulo
 * diretamente (CLAUDE.md §4).
 *
 * <p>Ambos os destinatários já vêm no evento ({@code customerId},
 * {@code providerUserId}) — sem chamada extra a outro módulo. Idempotente
 * por construção: {@link NotificationDispatcher} nesta onda só regista em
 * log, ver {@code LoggingNotificationDispatcher}.
 *
 * <p>{@code @Lazy}: ver nota em
 * {@code pt.servimatch.modules.users.internal.UserRepository}; o Spring
 * regista o {@code @ApplicationModuleListener} por introspeção do tipo, sem
 * instanciar o bean antes do primeiro evento.
 */
@Component
@Lazy
class ProposalNotificationListener {

    private final NotificationDispatcher dispatcher;

    ProposalNotificationListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @ApplicationModuleListener
    void on(ProposalAccepted event) {
        Map<String, String> payload = Map.of(
                "proposalId", event.proposalId().toString(),
                "requestId", event.requestId().toString());
        dispatcher.dispatch(event.customerId(), "PROPOSAL_ACCEPTED", payload);
        dispatcher.dispatch(event.providerUserId(), "PROPOSAL_ACCEPTED", payload);
    }
}
