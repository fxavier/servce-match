package pt.servimatch.modules.notifications.internal;

import java.util.Map;
import java.util.UUID;

/**
 * Porta de envio efetivo de uma notificação a um utilizador — o ponto único
 * a trocar quando o envio real (FCM/email) entrar, sem tocar nos listeners
 * de eventos que a chamam. Hoje só existe {@link LoggingNotificationDispatcher}
 * (regista em log; não há projeto Firebase configurado nesta onda).
 *
 * <p>Quando o envio real existir, a implementação terá de: (1) resolver os
 * {@code device_token} de {@code userId} (multi-dispositivo); (2) tentar
 * push (FCM) em cada um; (3) remover tokens que o FCM reporte como
 * inválidos; (4) cair para email se não houver nenhum token válido
 * (<em>fallback</em> push→email, CLAUDE.md/ARQUITETURA §... notificações).
 * Nada disto está implementado aqui — só o ponto de entrada.
 *
 * <p>{@code type}/{@code payload} não carregam PII para lá do necessário
 * (identificadores, não nomes/emails) — quem decide o texto final da
 * notificação (i18n, corpo da mensagem) é a implementação real, não o
 * chamador.
 */
public interface NotificationDispatcher {

    void dispatch(UUID userId, String type, Map<String, String> payload);
}
