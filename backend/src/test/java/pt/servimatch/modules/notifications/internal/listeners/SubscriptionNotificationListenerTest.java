package pt.servimatch.modules.notifications.internal.listeners;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.servimatch.modules.billing.events.SubscriptionActivated;
import pt.servimatch.modules.billing.events.SubscriptionPastDue;
import pt.servimatch.modules.notifications.internal.NotificationDispatcher;
import pt.servimatch.modules.providers.ProvidersApi;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prova a resolução {@code provider_profile.id → users.id} via
 * {@link ProvidersApi} antes de despachar (os eventos de {@code billing}
 * não carregam {@code users.id}), e o caso de erro: resolução falhada não
 * lança, só regista aviso e não despacha (idempotência perante reentrega).
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionNotificationListenerTest {

    @Mock
    private NotificationDispatcher dispatcher;
    @Mock
    private ProvidersApi providersApi;

    @Test
    void resolvesProviderUserIdBeforeDispatchingActivation() {
        SubscriptionNotificationListener listener = new SubscriptionNotificationListener(dispatcher, providersApi);
        UUID subscriptionId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID providerUserId = UUID.randomUUID();
        when(providersApi.findUserIdByProviderId(providerId)).thenReturn(Optional.of(providerUserId));

        listener.on(new SubscriptionActivated(subscriptionId, providerId, UUID.randomUUID(), Instant.now(), Instant.now(), Instant.now()));

        verify(dispatcher).dispatch(providerUserId, "SUBSCRIPTION_ACTIVATED", Map.of("subscriptionId", subscriptionId.toString()));
    }

    @Test
    void unresolvableProviderIsSkippedWithoutThrowing() {
        SubscriptionNotificationListener listener = new SubscriptionNotificationListener(dispatcher, providersApi);
        UUID providerId = UUID.randomUUID();
        when(providersApi.findUserIdByProviderId(providerId)).thenReturn(Optional.empty());

        listener.on(new SubscriptionPastDue(UUID.randomUUID(), providerId, Instant.now()));

        verifyNoInteractions(dispatcher);
    }
}
