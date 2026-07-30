import { api } from '../http';
import type { ProviderDashboardService } from '../interfaces';
import type { Proposal } from '../types';

/**
 * Sem endpoint de agregados no contrato (`GET /v1/providers/me/stats` ou
 * equivalente — pedido pendente ao `api-contract`, ver domainTypes.ts).
 * Deriva o que é possível a partir de `GET /v1/providers/me/requests`,
 * `GET /v1/proposals/me` e `GET /v1/subscriptions/me`; `last30Days` e
 * `estimatedRevenue` ficam a zero em vez de inventados — melhor um
 * dashboard incompleto e honesto do que números fictícios.
 */
export const providerDashboardServiceHttp: ProviderDashboardService = {
  async stats() {
    const [inboxResult, proposalsResult, subscriptionResult] = await Promise.all([
      api.GET('/v1/providers/me/requests', { params: { query: { limit: 1 } } }),
      api.GET('/v1/proposals/me', { params: { query: { limit: 100 } } }),
      api.GET('/v1/subscriptions/me'),
    ]);

    const newEligibleRequests = inboxResult.error ? 0 : inboxResult.data.items.length;
    const proposals: Proposal[] = proposalsResult.error ? [] : proposalsResult.data.items;

    const proposalsByStatus: Record<Proposal['status'], number> = {
      SENT: 0,
      ACCEPTED: 0,
      REJECTED: 0,
      CANCELLED: 0,
      EXPIRED: 0,
      SUPERSEDED: 0,
    };
    for (const proposal of proposals) {
      proposalsByStatus[proposal.status] += 1;
    }
    const decided = proposalsByStatus.ACCEPTED + proposalsByStatus.REJECTED;
    const acceptanceRate = decided > 0 ? Math.round((proposalsByStatus.ACCEPTED / decided) * 100) : 0;

    // 404 é "nunca subscreveu" (ver subscriptionsService); qualquer outro
    // erro também degrada para o mesmo estado neutro em vez de rebentar o
    // painel inteiro por causa de um agregado secundário.
    const subscriptionStatus = subscriptionResult.error ? ('NONE' as const) : subscriptionResult.data.status;

    return {
      subscriptionStatus,
      newEligibleRequests,
      proposalsByStatus,
      acceptanceRate,
      estimatedRevenue: { amountCents: 0, currency: 'EUR' },
      last30Days: [],
    };
  },
};
