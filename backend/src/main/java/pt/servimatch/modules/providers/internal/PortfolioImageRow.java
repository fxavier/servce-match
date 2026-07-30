package pt.servimatch.modules.providers.internal;

import java.util.UUID;

/** Linha de {@code provider_portfolio} (V18), ordem de posição preservada. */
record PortfolioImageRow(UUID imageAssetId, int position) {
}
