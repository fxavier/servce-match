/**
 * Payments — porta {@code PaymentGateway} e adaptadores (Stripe,
 * Eupago/IfthenPay); receção idempotente de webhooks (por
 * {@code raw_event_id}) e reconciliação. Ver ADR-0007.
 *
 * <p><b>Nunca</b> ativa uma subscrição a partir de um evento não
 * verificado do cliente — só por webhook autenticado do gateway.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-payments}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §12.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Payments"
)
package pt.servimatch.modules.payments;
