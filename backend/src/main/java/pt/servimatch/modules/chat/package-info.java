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
 *   <li>{@code providers} — via
 *       {@link pt.servimatch.modules.providers.ProvidersApi}, para saber se
 *       o utilizador é o prestador da conversa
 *       ({@code findProviderIdByUserId}) e para o gating por subscrição
 *       ({@code checkEligibility().visible()}, o mesmo {@code
 *       visibility_state} do prestador). Acrescentado nesta revisão (Onda
 *       1c, {@code backend-platform}) para substituir o SQL direto a
 *       {@code provider_profile} que o {@code ConversationRepository} do
 *       {@code backend-domain} usava por não ter esta dependência — ao
 *       contrário do {@code matching} (leitura <em>set-based</em>, plano de
 *       execução sensível), o chat lê um prestador por conversa, pelo que o
 *       argumento que justificaria SQL direto (ADR-0010) não se aplica
 *       aqui; a API Java chega.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Chat",
        allowedDependencies = {
                "modules.users", "modules.requests", "modules.proposals",
                "modules.uploads", "modules.providers"
        }
)
package pt.servimatch.modules.chat;
