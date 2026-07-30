package pt.servimatch.modules.providers.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Projeção de {@code provider_profile} (V4, V19). {@code visibility_state}
 * (V4) deliberadamente ausente: ADR-0011 D1 — nenhum predicado volta a
 * ler essa coluna, a elegibilidade compõe-se em
 * {@code ProvidersService.checkEligibility} a partir de
 * {@code approvalStatus} + {@code billing.SubscriptionLifecycle}. A coluna
 * em si só é removida numa migração futura (pedido a {@code db-migrations},
 * ver relatório de entrega); até lá fica na base de dados, sem escritor nem
 * leitor.
 */
record ProviderProfileRow(
        UUID id,
        UUID userId,
        String headline,
        String bio,
        String companyName,
        boolean verified,
        String approvalStatus,
        BigDecimal ratingAvg,
        int ratingCount,
        Double locationLat,
        Double locationLon,
        UUID avatarAssetId,
        Instant createdAt) {
}
