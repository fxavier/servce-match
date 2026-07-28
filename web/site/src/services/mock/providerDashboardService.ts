import type { ProviderDashboardService } from '../interfaces';
import { PROVIDER_CATEGORY_IDS } from './fixtures/providers';
import { mockCurrentUser } from './currentUser';
import { mockDb } from './db';
import { withLatency } from './latency';

export const providerDashboardServiceMock: ProviderDashboardService = {
  stats() {
    return withLatency(() => {
      const user = mockCurrentUser.get();
      const categoryIds = user ? (PROVIDER_CATEGORY_IDS[user.id] ?? []) : [];
      const newEligibleRequests = mockDb.requests.filter(
        (r) => r.status === 'PUBLISHED' && categoryIds.includes(r.category?.id ?? ''),
      ).length;
      const mine = mockDb.proposals.filter((p) => p.providerId === user?.id);
      const proposalsByStatus = {
        SENT: mine.filter((p) => p.status === 'SENT').length,
        ACCEPTED: mine.filter((p) => p.status === 'ACCEPTED').length,
        REJECTED: mine.filter((p) => p.status === 'REJECTED').length,
        CANCELLED: mine.filter((p) => p.status === 'CANCELLED').length,
        EXPIRED: mine.filter((p) => p.status === 'EXPIRED').length,
        SUPERSEDED: mine.filter((p) => p.status === 'SUPERSEDED').length,
      };
      const decided = proposalsByStatus.ACCEPTED + proposalsByStatus.REJECTED;
      const acceptanceRate = decided > 0 ? Math.round((proposalsByStatus.ACCEPTED / decided) * 100) : 0;
      const revenueCents = mine
        .filter((p) => p.status === 'ACCEPTED')
        .reduce((sum, p) => sum + p.price.amountCents, 0);
      const today = new Date();
      const last30Days = Array.from({ length: 30 }).map((_, index) => {
        const date = new Date(today);
        date.setDate(date.getDate() - (29 - index));
        return {
          date: date.toISOString().slice(0, 10),
          proposalsSent: index % 6 === 0 ? 1 : 0,
          proposalsAccepted: index % 11 === 0 ? 1 : 0,
        };
      });
      return {
        subscriptionStatus: mockDb.providerSubscriptionActive ? 'ACTIVE' : 'EXPIRED',
        newEligibleRequests,
        proposalsByStatus,
        acceptanceRate,
        estimatedRevenue: { amountCents: revenueCents, currency: 'EUR' },
        last30Days,
      };
    });
  },
};
