/**
 * Proposals — propostas dos prestadores a pedidos e a respetiva máquina de
 * estados ({@code SENT → ACCEPTED / REJECTED / CANCELLED / EXPIRED /
 * SUPERSEDED}). Único registo não-terminal por par (pedido, prestador).
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-domain}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §4.4, §6.3.
 *
 * <p>{@code allowedDependencies} fechado nesta revisão (Onda 1b,
 * {@code backend-platform}), a partir dos imports reais: {@code requests}
 * ({@code RequestsApi}, para validar o pedido alvo), {@code providers}
 * ({@code ProvidersApi}) e {@code users} ({@code UsersApi}). Um módulo que
 * precise de uma dependência nova pede-a a este agente.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Proposals",
        allowedDependencies = {"modules.requests", "modules.providers", "modules.users"}
)
package pt.servimatch.modules.proposals;
