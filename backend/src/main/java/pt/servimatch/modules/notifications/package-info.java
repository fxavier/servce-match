/**
 * Notifications — registo de {@code DeviceToken} multi-dispositivo
 * ({@code POST}/{@code DELETE /v1/device-tokens}) e despacho de
 * notificações a partir de <b>eventos de domínio subscritos</b>, nunca por
 * chamada direta de um módulo de domínio (CLAUDE.md §4: "Notificações...
 * a partir de eventos de domínio subscritos"). Push (FCM) e email reais
 * ficam fora desta entrega — não há projeto Firebase configurado, e o
 * {@code mobile-flutter} já deixou o seu ponto de entrada preparado do lado
 * dele pela mesma razão. A fronteira fica pronta para o envio real entrar
 * depois, sem alterar os consumidores (listeners e controlador):
 * {@link pt.servimatch.modules.notifications.internal.NotificationDispatcher}
 * é a porta a trocar (hoje só regista em log,
 * {@link pt.servimatch.modules.notifications.internal.LoggingNotificationDispatcher}).
 *
 * <p>Módulo transversal, propriedade do agente {@code backend-platform}
 * (como {@code uploads}): não tem semântica de domínio própria, só
 * infraestrutura de entrega reutilizada a partir de eventos de outros
 * módulos.
 *
 * <p>{@code allowedDependencies}:
 * <ul>
 *   <li>{@code users} — {@code UsersApi.ensureProvisioned}, para o
 *       {@code sub} do JWT no controlador de {@code device-tokens}.</li>
 *   <li>{@code proposals} — evento {@code ProposalAccepted} (pacote de
 *       topo), consumido por {@code @ApplicationModuleListener}: confirma a
 *       ambas as partes ({@code customerId} e {@code providerUserId}, já
 *       incluídos no próprio evento — sem chamada extra).</li>
 *   <li>{@code providers} — {@code ProvidersApi.findUserIdByProviderId},
 *       para resolver o {@code provider_profile.id} dos eventos de
 *       {@code billing} (abaixo) para o {@code users.id} usado por
 *       {@code device_token.user_id}.</li>
 *   <li>{@code billing::events} — interface nomeada (ver
 *       {@code pt.servimatch.modules.billing.events.package-info}), para
 *       {@code SubscriptionActivated}/{@code PastDue}/{@code Expired}/
 *       {@code Cancelled}.</li>
 * </ul>
 *
 * <p><b>Nota sobre "os três agentes de backend" (ver relatório de
 * entrega):</b> nesta revisão só {@code backend-domain} ({@code proposals})
 * e {@code backend-payments} ({@code billing}) têm eventos reais a
 * subscrever. {@code backend-matching} ({@code matching}/{@code geo}/
 * {@code search}) ainda não publica nenhum evento de domínio — expõe hoje
 * só {@code MatchingApi} síncrona (ver
 * {@code pt.servimatch.modules.matching.package-info}). Quando existir um
 * evento assíncrono desse lado, um listener entra aqui; nada a fazer
 * entretanto.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Notifications",
        allowedDependencies = {"modules.users", "modules.proposals", "modules.providers", "modules.billing::events"}
)
package pt.servimatch.modules.notifications;
