package pt.servimatch.modules.proposals;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado depois de, na mesma transação: a proposta transitar para
 * {@code ACCEPTED}, o pedido para {@code CONFIRMED}, e as restantes
 * propostas do pedido para {@code SUPERSEDED} (CLAUDE.md — transição
 * central; ver nota de bloqueio otimista em {@code ProposalsService.accept}).
 *
 * <p><b>Nota de estrutura:</b> vive no pacote de topo do módulo (não em
 * {@code events/}, como sugerido pela skill {@code spring-modulith-module})
 * porque o Spring Modulith só expõe por omissão o pacote de topo de um
 * módulo como API pública; um subpacote {@code events} exigiria um
 * {@code @NamedInterface} num {@code package-info.java} próprio — ficheiro
 * que, por CLAUDE.md §3, é propriedade do {@code backend-platform}. Ver
 * relatório de entrega (pedido explícito ao {@code backend-platform} para
 * decidir se prefere essa convenção).
 *
 * <p><b>Não</b> carrega {@code bookingId}: a {@code Booking} é criada pelo
 * módulo {@code bookings} a reagir a este evento via
 * {@code @ApplicationModuleListener} (não por chamada síncrona a partir de
 * {@code proposals} — essa direção síncrona criaria um ciclo de módulos com
 * {@code bookings}, que depende de {@code proposals} para resolver
 * participantes, cf. ARQUITETURA §6.3 "schedule depende de proposals"; ver
 * relatório de entrega). É por isso <em>eventualmente consistente</em>: há
 * uma janela, tipicamente de milissegundos, entre a proposta ficar
 * {@code ACCEPTED} e a {@code Booking} existir.
 *
 * <p><b>Consumidores previstos na Onda 1b</b> (fora do âmbito deste
 * agente): o módulo {@code chat} cria a {@code Conversation}
 * {@code (requestId, customerId, providerId)} a partir deste evento — ver
 * {@code conversation} em V9, único por {@code (request_id, provider_id)};
 * o módulo {@code notifications} despoleta a notificação de confirmação a
 * ambas as partes.
 *
 * <p>Carrega identificadores mínimos, não o agregado completo (skill
 * {@code spring-modulith-module}). Consumidores têm de ser idempotentes
 * (entrega <em>at-least-once</em>).
 *
 * @param proposalId     proposta aceite
 * @param requestId      pedido agora {@code CONFIRMED}
 * @param customerId     {@code users.id} do dono do pedido
 * @param providerId     {@code provider_profile.id} do prestador
 * @param providerUserId {@code users.id} do utilizador por trás do prestador
 *                       (conveniência para notificações/push por utilizador)
 * @param acceptedAt     instante da aceitação
 */
public record ProposalAccepted(
        UUID proposalId,
        UUID requestId,
        UUID customerId,
        UUID providerId,
        UUID providerUserId,
        Instant acceptedAt) {
}
