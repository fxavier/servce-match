/**
 * Billing — planos de subscrição e ciclo de vida da subscrição do
 * prestador ({@code PENDING → ACTIVE → PAST_DUE/EXPIRED → CANCELLED}).
 *
 * <p>Implementado pelo agente {@code backend-payments} (ver
 * {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §12). Este ficheiro,
 * tal como o de qualquer outro módulo, é escrito exclusivamente pelo agente
 * {@code backend-platform} (CLAUDE.md §3).
 *
 * <p><b>{@code allowedDependencies} vazio — decidido, não pendente
 * (ADR-0011 D8).</b> O pedido histórico de adicionar {@code providers}
 * (e/ou {@code users}), registado numa revisão anterior por causa de
 * {@code billing.internal.JdbcProviderAccountResolver}
 * ({@code ProviderAccountResolver}: resolve {@code sub Keycloak →
 * provider_profile.id} lendo diretamente {@code users}/
 * {@code provider_profile} em SQL bruto, nunca autorizado pelo ADR-0010 —
 * não é uma consulta <em>set-based</em>), fica <b>recusado com
 * fundamento</b>: o ADR-0011 (Racional, D8) verificou que o único
 * consumidor de {@code ProviderAccountResolver} é
 * {@code payments.web.SubscriptionController}, e nenhuma lógica de domínio
 * de {@code billing} precisa dele. A resolução de identidade não é uma
 * capacidade de {@code billing} — é composição de {@code UsersApi} +
 * {@code ProvidersApi}, que já vive corretamente do lado de
 * {@code payments} (ver {@code payments.package-info}).
 *
 * <p><b>Trabalho pendente, fora deste ficheiro:</b>
 * {@code ProviderAccountResolver} e {@code JdbcProviderAccountResolver}
 * saem de {@code billing}; {@code SubscriptionController} passa a resolver
 * {@code sub → providerId} com {@code UsersApi.ensureProvisioned} +
 * {@code ProvidersApi.findProviderIdByUserId}. Código de
 * {@code backend-payments}, fora do âmbito de escrita deste agente.
 *
 * <p><b>Direção fixada, sem ciclo:</b> {@code billing → {}};
 * {@code providers → {users, billing}} (ver {@code providers.package-info});
 * {@code payments → {billing, providers, users}} (ver
 * {@code payments.package-info}). Se um dia {@code billing} precisar mesmo
 * de {@code providers}, o caminho é um evento ou uma inversão por
 * interface — nunca a reabertura direta, que fecharia um ciclo com
 * {@code providers → billing} já em vigor.
 *
 * <p>Convenção de eventos de domínio (decisão da onda anterior, ver também
 * {@code pt.servimatch.modules.chat.package-info}): eventos consumidos por
 * <b>outro</b> módulo vivem, em geral, no pacote de topo do módulo
 * publicador, não num subpacote {@code events} sem {@code @NamedInterface}
 * — o Modulith só expõe por omissão o pacote de topo, e {@code proposals}/
 * {@code requests} moveram os seus eventos para lá por este motivo. O
 * subpacote {@code billing.events} (sem {@code internal}) já existia com
 * essa forma antes de a convenção ficar fixada; ficou sinalizado, mas não
 * resolvido, na revisão anterior, por não ter consumidor externo.
 *
 * <p><b>Resolvido nesta revisão (Onda 1b):</b> {@code modules.notifications}
 * passou a consumir {@code SubscriptionActivated}/{@code PastDue}/
 * {@code Expired}/{@code Cancelled} via {@code @ApplicationModuleListener},
 * o que teria partido {@code verify()}. Mover os quatro registos de evento
 * para o pacote de topo de {@code billing} alteraria código de
 * {@code backend-payments} — fora do âmbito de escrita deste agente. Em vez
 * disso, {@code billing.events} passou a ter {@code @NamedInterface("events")}
 * (ver {@code pt.servimatch.modules.billing.events.package-info}) — a
 * correção mais pequena, sem tocar em código de domínio. Se
 * {@code backend-payments} preferir mover os eventos para o pacote de topo
 * no futuro (alinhando com {@code proposals}/{@code requests}), a
 * {@code @NamedInterface} deixa de ser necessária; pedido nesse sentido a
 * este agente, não uma decisão unilateral de {@code backend-payments}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Billing",
        allowedDependencies = {}
)
package pt.servimatch.modules.billing;
