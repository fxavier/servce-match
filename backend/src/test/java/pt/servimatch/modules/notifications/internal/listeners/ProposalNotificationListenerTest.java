package pt.servimatch.modules.notifications.internal.listeners;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.servimatch.modules.notifications.internal.NotificationDispatcher;
import pt.servimatch.modules.proposals.ProposalAccepted;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;

/**
 * Prova que {@code ProposalAccepted} despoleta a notificação a ambas as
 * partes (cliente e prestador), diretamente a partir do evento — sem
 * chamada adicional a outro módulo, ver javadoc da classe sob teste.
 */
@ExtendWith(MockitoExtension.class)
class ProposalNotificationListenerTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @Test
    void notifiesBothCustomerAndProvider() {
        ProposalNotificationListener listener = new ProposalNotificationListener(dispatcher);

        UUID proposalId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID providerUserId = UUID.randomUUID();

        listener.on(new ProposalAccepted(proposalId, requestId, customerId, providerId, providerUserId, Instant.now()));

        Map<String, String> expectedPayload = Map.of("proposalId", proposalId.toString(), "requestId", requestId.toString());
        verify(dispatcher).dispatch(customerId, "PROPOSAL_ACCEPTED", expectedPayload);
        verify(dispatcher).dispatch(providerUserId, "PROPOSAL_ACCEPTED", expectedPayload);
    }
}
