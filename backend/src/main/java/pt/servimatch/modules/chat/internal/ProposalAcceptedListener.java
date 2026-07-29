package pt.servimatch.modules.chat.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.servimatch.modules.proposals.ProposalAccepted;

/**
 * Cria a {@code Conversation (requestId, customerId, providerId)} quando uma
 * proposta é aceite — mesmo padrão que
 * {@code bookings.internal.ProposalAcceptedListener}: reage ao evento em vez
 * de {@code proposals} chamar este módulo sincronamente (CLAUDE.md — "a
 * Conversation nasce de ProposalAccepted, consumido por
 * {@code @ApplicationModuleListener} — não por chamada direta a partir de
 * proposals"). {@code ensureConversation} é idempotente ({@code
 * uq_conversation_request_provider}, V9), como exige a entrega
 * <em>at-least-once</em> do {@code @ApplicationModuleListener}.
 *
 * <p>Usa {@code event.providerId()} (já {@code provider_profile.id}) e
 * {@code event.customerId()} diretamente do evento — não precisa de
 * resolver participantes via outro módulo.
 *
 * <p>{@code @Lazy}: ver nota em {@code bookings.internal.ProposalAcceptedListener}.
 */
// Nome de bean explícito: "proposalAcceptedListener" colidiria com
// bookings.internal.ProposalAcceptedListener (mesmo simple name, pacotes
// diferentes) — o Spring deriva o nome por omissão só do simple name da
// classe, não do pacote completo.
@Component("chatProposalAcceptedListener")
@Lazy
class ProposalAcceptedListener {

    private static final Logger log = LoggerFactory.getLogger(ProposalAcceptedListener.class);

    private final ChatService chatService;

    ProposalAcceptedListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @ApplicationModuleListener
    void on(ProposalAccepted event) {
        chatService.ensureConversation(event.requestId(), event.customerId(), event.providerId());
        log.info("Conversation garantida para requestId/providerId (correlação por evento, não por PII)");
    }
}
