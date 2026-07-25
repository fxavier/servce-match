/**
 * Requests — pedidos de serviço do cliente e a respetiva máquina de
 * estados ({@code DRAFT → PUBLISHED → IN_NEGOTIATION → CONFIRMED →
 * IN_PROGRESS → COMPLETED}, com {@code CANCELLED} como saída lateral).
 * Publica {@code RequestPublished} para acionar o matching assíncrono.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-domain}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §4.3, §6.3.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Requests"
)
package pt.servimatch.modules.requests;
