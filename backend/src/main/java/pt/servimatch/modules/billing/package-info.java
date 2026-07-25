/**
 * Billing — planos de subscrição e ciclo de vida da subscrição do
 * prestador ({@code PENDING → ACTIVE → PAST_DUE/EXPIRED → CANCELLED}).
 * {@code visibility_state} do prestador deriva do estado da subscrição.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-payments}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §12.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Billing"
)
package pt.servimatch.modules.billing;
