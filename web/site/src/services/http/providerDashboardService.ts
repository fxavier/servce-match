import { api } from '../http';
import type { ProviderDashboardService } from '../interfaces';

/**
 * Sem endpoint de agregados no contrato (gap #9 — `GET /v1/providers/me/stats`
 * pendente). Deriva o que é possível a partir da inbox real; o resto fica a
 * zero em vez de inventado — melhor um dashboard incompleto e honesto do
 * que números fictícios.
 */
export const providerDashboardServiceHttp: ProviderDashboardService = {
  async stats() {
    const { data, error } = await api.GET('/v1/providers/me/requests', { params: { query: { limit: 1 } } });
    const newEligibleRequests = error ? 0 : data.items.length;
    return {
      subscriptionStatus: 'PENDING',
      newEligibleRequests,
      proposalsByStatus: { SENT: 0, ACCEPTED: 0, REJECTED: 0, CANCELLED: 0, EXPIRED: 0, SUPERSEDED: 0 },
      acceptanceRate: 0,
      estimatedRevenue: { amountCents: 0, currency: 'EUR' },
      last30Days: [],
    };
  },
};
