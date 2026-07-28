/**
 * Chat — conversas e mensagens entre cliente e prestador (ARQUITETURA
 * §6.3/§11.3). O transporte em tempo real ({@code /ws}, STOMP, autenticação
 * no <em>handshake</em>, <em>relay</em> externo em multi-instância) é do
 * agente {@code backend-platform}; conversas, mensagens e a autorização de
 * acesso são deste módulo, do agente {@code backend-domain}.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform}. Implementação
 * pertence ao agente {@code backend-domain} — ver {@code docs/AGENTES.md}.
 *
 * <p>{@code allowedDependencies}:
 * <ul>
 *   <li>{@code users} — identidade dos participantes.</li>
 *   <li>{@code requests} — uma conversa negoceia um pedido; ARQUITETURA §6.3
 *       ("chat depende de requests, users").</li>
 *   <li>{@code proposals} — abre a conversa reagindo a
 *       {@code ProposalAccepted} ({@code @ApplicationModuleListener}, mesmo
 *       padrão que {@code bookings.internal.ProposalAcceptedListener}); o
 *       evento vive no pacote de topo de {@code proposals} (ver decisão
 *       registada em {@code pt.servimatch.modules.proposals.package-info}
 *       — não em {@code proposals.events}), por isso a dependência é
 *       diretamente sobre {@code proposals}, sem interface nomeada extra.</li>
 *   <li>{@code uploads} — validação/resolução de anexos de mensagem
 *       ({@code MESSAGE_ATTACHMENT}) via
 *       {@link pt.servimatch.modules.uploads.UploadsApi}, nunca lendo
 *       {@code upload_asset} diretamente (CLAUDE.md §4).</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Chat",
        allowedDependencies = {"modules.users", "modules.requests", "modules.proposals", "modules.uploads"}
)
package pt.servimatch.modules.chat;
