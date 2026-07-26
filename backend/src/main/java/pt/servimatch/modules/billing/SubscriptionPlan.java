package pt.servimatch.modules.billing;

import java.util.UUID;

/**
 * Plano de subscrição (ARQUITETURA §3.4, §9.2). Limites como dados, não
 * constantes no código — {@code null} em {@code maxCategories}/{@code maxAreas}
 * significa "ilimitado" (ver contrato {@code SubscriptionPlan}).
 */
public record SubscriptionPlan(
        UUID id,
        String code,
        String name,
        long priceAmountCents,
        String priceCurrency,
        String billingInterval,
        Integer maxCategories,
        Integer maxAreas,
        int rankingBoost,
        boolean hasBadge,
        boolean active) {
}
