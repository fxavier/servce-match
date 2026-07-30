/**
 * Payments — porta {@code PaymentGateway} e adaptadores (Stripe,
 * Eupago/IfthenPay); receção idempotente de webhooks (por
 * {@code raw_event_id}) e reconciliação. Ver ADR-0007.
 *
 * <p><b>Nunca</b> ativa uma subscrição a partir de um evento não
 * verificado do cliente — só por webhook autenticado do gateway.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform}. Implementação
 * pertence ao agente {@code backend-payments} — ver {@code docs/AGENTES.md}
 * e {@code docs/ARQUITETURA.md} §12.
 *
 * <p>{@code allowedDependencies} (revisão "dados-reais", Onda 1,
 * {@code backend-platform}, ADR-0011 D8):
 * <ul>
 *   <li>{@code billing} (pacote de topo) — {@code Subscription},
 *       {@code SubscriptionLifecycle}, {@code SubscriptionPlan},
 *       {@code SubscriptionStatus}: orquestra o checkout sem reimplementar
 *       o ciclo de vida da subscrição.</li>
 *   <li>{@code providers} — <b>nova nesta revisão.</b>
 *       {@code ProvidersApi.findProviderIdByUserId}, para resolver
 *       identidade → prestador em {@code SubscriptionController} (endpoints
 *       {@code POST /v1/subscriptions} e {@code GET /v1/subscriptions/me}),
 *       substituindo {@code billing.ProviderAccountResolver}/
 *       {@code JdbcProviderAccountResolver} — ver o racional completo em
 *       {@code billing.package-info}.</li>
 *   <li>{@code users} — <b>nova nesta revisão.</b>
 *       {@code UsersApi.ensureProvisioned}, para o {@code sub} do JWT antes
 *       de o passar a {@code ProvidersApi}. Mesmo padrão que qualquer outro
 *       controlador autenticado (ver {@code RequestsController},
 *       {@code ProposalsController}).</li>
 * </ul>
 *
 * <p>Um módulo que precise de uma dependência nova pede-a a este agente.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Payments",
        allowedDependencies = {"modules.billing", "modules.providers", "modules.users"}
)
package pt.servimatch.modules.payments;
