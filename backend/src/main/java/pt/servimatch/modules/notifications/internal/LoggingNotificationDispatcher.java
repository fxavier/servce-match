package pt.servimatch.modules.notifications.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Única implementação de {@link NotificationDispatcher} nesta onda: regista
 * em log estruturado a intenção de notificar (com contagem de dispositivos
 * já registados para {@code userId}, prova de que o registo de
 * {@code device_token} está de facto ligado ao despacho), sem enviar nada.
 * Nunca falha (não há efeito colateral a reverter), o que faz dos
 * consumidores ({@code @ApplicationModuleListener}) idempotentes por
 * construção — repetir a entrega (at-least-once, Event Publication
 * Registry) só repete a mesma linha de log.
 *
 * <p>Sem PII: {@code userId} é um identificador opaco, não nome/email.
 */
@Component
@Lazy
class LoggingNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationDispatcher.class);

    private final DeviceTokenRepository repository;

    LoggingNotificationDispatcher(DeviceTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public void dispatch(UUID userId, String type, Map<String, String> payload) {
        List<DeviceTokenRow> tokens = repository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.info("Notificação '{}' para userId={}: sem device_token registado (fallback para email fica para quando esse canal existir); payload={}",
                    type, userId, payload);
            return;
        }
        log.info("Notificação '{}' para userId={}: pronta a enviar a {} dispositivo(s) registado(s); envio real (FCM) fica para quando o projeto Firebase existir; payload={}",
                type, userId, tokens.size(), payload);
    }
}
