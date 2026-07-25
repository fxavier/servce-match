/**
 * Proposals — propostas dos prestadores a pedidos e a respetiva máquina de
 * estados ({@code SENT → ACCEPTED / REJECTED / CANCELLED / EXPIRED /
 * SUPERSEDED}). Único registo não-terminal por par (pedido, prestador).
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-domain}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §4.4, §6.3.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Proposals"
)
package pt.servimatch.modules.proposals;
