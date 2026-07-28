/**
 * Interface nomeada {@code events} do módulo {@code billing}: os eventos de
 * ciclo de vida da subscrição ({@link pt.servimatch.modules.billing.events.SubscriptionActivated},
 * {@link pt.servimatch.modules.billing.events.SubscriptionPastDue},
 * {@link pt.servimatch.modules.billing.events.SubscriptionExpired},
 * {@link pt.servimatch.modules.billing.events.SubscriptionCancelled}),
 * publicados no Event Publication Registry (ADR-0001) e consumidos de forma
 * assíncrona por outros módulos via {@code @ApplicationModuleListener}.
 *
 * <p>Sem {@code @NamedInterface}, o Modulith só expõe por omissão o pacote
 * de topo de um módulo como API pública — a mesma razão pela qual
 * {@code proposals} e {@code requests} movem os seus eventos para o pacote
 * de topo em vez de um subpacote {@code events} (ver
 * {@code pt.servimatch.modules.proposals.ProposalAccepted}). O pacote
 * {@code billing.events} já existia com esta forma antes de essa convenção
 * ficar fixada, e passava {@code ApplicationModules.verify()} só porque não
 * tinha consumidor externo (ver nota, agora resolvida, em
 * {@code pt.servimatch.modules.billing.package-info}).
 *
 * <p>Com {@code modules.notifications} a consumir estes quatro eventos
 * (Onda 1b), mover os registos para o pacote de topo de {@code billing}
 * exigiria alterar código do agente {@code backend-payments} — fora do
 * âmbito de escrita deste agente ({@code package-info.java} apenas). Expor
 * este subpacote como interface nomeada é a alternativa mais pequena:
 * mantém {@code verify()} verde sem tocar em código de domínio de
 * {@code billing}. Referenciada em {@code allowedDependencies} de um módulo
 * consumidor como {@code "modules.billing::events"}.
 */
@org.springframework.modulith.NamedInterface("events")
package pt.servimatch.modules.billing.events;
