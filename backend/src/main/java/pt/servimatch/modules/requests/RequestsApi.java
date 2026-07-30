package pt.servimatch.modules.requests;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública do módulo {@code requests}, usada sincronamente pelo módulo
 * {@code proposals} para as transições que têm de acontecer na mesma
 * transação que a aceitação de uma proposta (CLAUDE.md — "aceitar uma
 * proposta confirma o pedido... numa única transação"), e por {@code chat}
 * para resolver {@code requestTitle} em {@code listConversations}.
 */
public interface RequestsApi {

    Optional<RequestSummary> get(UUID requestId);

    /**
     * Títulos de pedidos em lote, por id — para consumidores que precisam
     * de {@code title} de várias linhas de uma página sem N+1 (ex.
     * {@code chat.internal.ChatService#listConversations}, uma única
     * chamada com todos os {@code requestId} da página). {@code requestId}
     * inexistente fica ausente do mapa (não lança erro); o chamador decide
     * o texto de fallback.
     */
    Map<UUID, String> findTitlesByIds(Collection<UUID> requestIds);

    /**
     * {@code PUBLISHED → IN_NEGOTIATION} (ARQUITETURA §4.3: "1ª proposta").
     * Idempotente: não faz nada se o pedido já estiver {@code IN_NEGOTIATION}.
     * Lança {@code 409} (via {@link org.springframework.web.ErrorResponseException})
     * se o pedido estiver noutro estado incompatível (ex.: {@code DRAFT},
     * {@code CANCELLED}) e {@code 404} se não existir.
     */
    void markInNegotiation(UUID requestId);

    /**
     * {@code IN_NEGOTIATION → CONFIRMED} (ARQUITETURA §4.3: "cliente aceita
     * proposta"). Implementado como <em>compare-and-swap</em> ao nível da
     * linha (bloqueio otimista sem coluna de versão, CLAUDE.md — corrida
     * entre aceitações concorrentes resolve para exatamente uma): lança
     * {@code 409} se o pedido não estiver, neste preciso instante,
     * {@code IN_NEGOTIATION}.
     */
    void confirm(UUID requestId);

    record RequestSummary(UUID id, UUID customerId, UUID categoryId, ServiceRequestStatus status) {
    }
}
