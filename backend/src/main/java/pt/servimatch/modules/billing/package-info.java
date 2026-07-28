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
 * <p>Convenção de eventos de domínio (decisão desta onda, ver também
 * {@code pt.servimatch.modules.chat.package-info}): eventos consumidos por
 * <b>outro</b> módulo vivem no pacote de topo do módulo publicador, não num
 * subpacote {@code events} sem {@code @NamedInterface} — o Modulith só
 * expõe por omissão o pacote de topo, e {@code proposals} já teve de mover
 * {@code ProposalAccepted} para lá por este motivo (pedido do
 * {@code backend-domain} nesta onda). O subpacote
 * {@code billing.internal.events}... na verdade {@code billing.events}
 * (sem {@code internal}) já existe aqui e, hoje, nada fora de
 * {@code billing} o importa — não é uma violação em vigor, só uma
 * inconsistência com a convenção agora fixada. Não movido nesta revisão
 * (fora do âmbito de escrita deste agente); sinalizado a
 * {@code backend-payments} caso algum consumidor externo (ex.
 * {@code notifications}) venha a precisar de {@code SubscriptionActivated}
 * e afins.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Billing",
        allowedDependencies = {}
)
package pt.servimatch.modules.billing;
