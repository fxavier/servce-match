package pt.servimatch.modules.billing.web.dto;

import pt.servimatch.modules.billing.SubscriptionPlan;

import java.util.UUID;

/** Espelha o schema {@code SubscriptionPlan} do contrato ({@code docs/api/openapi.yaml}). */
public record SubscriptionPlanResponse(
        UUID id,
        String code,
        String name,
        MoneyDto price,
        String interval,
        Integer maxCategories,
        Integer maxAreas,
        int rankingBoost,
        boolean hasBadge,
        boolean active) {

    public static SubscriptionPlanResponse from(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.id(),
                plan.code(),
                plan.name(),
                new MoneyDto(plan.priceAmountCents(), plan.priceCurrency()),
                plan.billingInterval(),
                plan.maxCategories(),
                plan.maxAreas(),
                plan.rankingBoost(),
                plan.hasBadge(),
                plan.active());
    }
}
