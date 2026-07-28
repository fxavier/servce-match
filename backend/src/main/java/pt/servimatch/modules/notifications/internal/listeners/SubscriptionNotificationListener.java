package pt.servimatch.modules.notifications.internal.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.servimatch.modules.billing.events.SubscriptionActivated;
import pt.servimatch.modules.billing.events.SubscriptionCancelled;
import pt.servimatch.modules.billing.events.SubscriptionExpired;
import pt.servimatch.modules.billing.events.SubscriptionPastDue;
import pt.servimatch.modules.notifications.internal.NotificationDispatcher;
import pt.servimatch.modules.providers.ProvidersApi;

import java.util.Map;
import java.util.UUID;

/**
 * Notifica o prestador das transições do ciclo de vida da subscrição
 * ({@code billing}, agente {@code backend-payments}). Reage por evento —
 * {@code billing} nunca chama {@code notifications} diretamente (CLAUDE.md
 * §4). Os quatro eventos vivem em {@code billing.events}, exposto como
 * interface nomeada ({@code @NamedInterface("events")}, ver
 * {@code pt.servimatch.modules.billing.events.package-info}) — decisão
 * desta onda para não exigir alterar código de {@code backend-payments}
 * (ver {@code pt.servimatch.modules.billing.package-info}).
 *
 * <p>Os eventos só carregam {@code providerId} ({@code provider_profile.id},
 * não {@code users.id}); resolve-se para o utilizador via
 * {@link ProvidersApi#findUserIdByProviderId(UUID)} antes de despachar —
 * {@code device_token.user_id} referencia {@code users}, não
 * {@code provider_profile}. Se a resolução falhar (prestador removido
 * entretanto, corrida rara), a notificação é ignorada com aviso em log, não
 * um erro — o handler continua idempotente perante reentrega.
 *
 * <p>{@code @Lazy}: ver nota em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@Component
@Lazy
class SubscriptionNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionNotificationListener.class);

    private final NotificationDispatcher dispatcher;
    private final ProvidersApi providersApi;

    SubscriptionNotificationListener(NotificationDispatcher dispatcher, ProvidersApi providersApi) {
        this.dispatcher = dispatcher;
        this.providersApi = providersApi;
    }

    @ApplicationModuleListener
    void on(SubscriptionActivated event) {
        notifyProvider(event.providerId(), "SUBSCRIPTION_ACTIVATED",
                Map.of("subscriptionId", event.subscriptionId().toString()));
    }

    @ApplicationModuleListener
    void on(SubscriptionPastDue event) {
        notifyProvider(event.providerId(), "SUBSCRIPTION_PAST_DUE",
                Map.of("subscriptionId", event.subscriptionId().toString()));
    }

    @ApplicationModuleListener
    void on(SubscriptionExpired event) {
        notifyProvider(event.providerId(), "SUBSCRIPTION_EXPIRED",
                Map.of("subscriptionId", event.subscriptionId().toString()));
    }

    @ApplicationModuleListener
    void on(SubscriptionCancelled event) {
        notifyProvider(event.providerId(), "SUBSCRIPTION_CANCELLED",
                Map.of("subscriptionId", event.subscriptionId().toString()));
    }

    private void notifyProvider(UUID providerId, String type, Map<String, String> payload) {
        providersApi.findUserIdByProviderId(providerId)
                .ifPresentOrElse(
                        userId -> dispatcher.dispatch(userId, type, payload),
                        () -> log.warn("Notificação '{}' ignorada: sem users.id para providerId={} (prestador removido?).", type, providerId));
    }
}
