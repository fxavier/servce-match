/**
 * Billing — planos de subscrição e ciclo de vida da subscrição do
 * prestador ({@code PENDING → ACTIVE → PAST_DUE/EXPIRED → CANCELLED}).
 * {@code visibility_state} do prestador deriva do estado da subscrição.
 *
 * <p>Implementado pelo agente {@code backend-payments} (ver
 * {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §12). Este ficheiro,
 * tal como o de qualquer outro módulo, é escrito exclusivamente pelo agente
 * {@code backend-platform} (CLAUDE.md §3) — {@code backend-payments}
 * criou-o antes de essa regra estar em vigor; esta revisão assume-o, sem
 * alterações de fundo.
 *
 * <p><b>{@code allowedDependencies} vazio, por agora.</b>
 * {@code ARQUITETURA.md} §6.3 lista {@code subscriptions} (=este módulo,
 * {@code billing}) como dependente de {@code providers}. Na prática,
 * {@code billing.internal.JdbcProviderAccountResolver} já documenta esse
 * mesmo facto (javadoc do próprio ficheiro: resolve
 * {@code sub Keycloak → provider_profile.id} lendo diretamente as tabelas
 * {@code users}/{@code provider_profile} em SQL bruto, como atalho
 * enquanto os módulos {@code users}/{@code providers} não existiam neste
 * worktree). Esses módulos existem agora neste monólito integrado — mas
 * trocar o SQL bruto por {@code UsersApi}/{@code ProvidersApi} é uma
 * alteração ao código de {@code billing}, fora do âmbito de escrita deste
 * agente (só o {@code package-info.java}). Pedido ao agente
 * {@code backend-payments}: ao fazer essa troca, pedir aqui a adição de
 * {@code "providers"} (e/ou {@code "users"}) a {@code allowedDependencies}.
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
