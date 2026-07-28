import type { SubscriptionsService } from '../interfaces';
import type { Subscription, SubscriptionCheckout } from '../types';
import { PLANS } from './fixtures/plans';
import { mockCurrentUser } from './currentUser';
import { mockDb } from './db';
import { withLatency } from './latency';

export const subscriptionsServiceMock: SubscriptionsService = {
  listPlans() {
    return withLatency(() => PLANS);
  },
  current() {
    return withLatency<Subscription | undefined>(() => {
      const user = mockCurrentUser.get();
      if (!user || user.roles[0] !== 'PROVIDER') return undefined;
      const plan = PLANS[1];
      const now = new Date();
      const periodEnd = new Date(now);
      periodEnd.setDate(periodEnd.getDate() + 30);
      return {
        id: 'sub-mock-0001',
        providerId: user.id,
        planId: plan.id,
        status: mockDb.providerSubscriptionActive ? 'ACTIVE' : 'EXPIRED',
        currentPeriodStart: now.toISOString(),
        currentPeriodEnd: periodEnd.toISOString(),
        cancelAtPeriodEnd: false,
      };
    });
  },
  create(body) {
    return withLatency<SubscriptionCheckout>(() => {
      const isMultibanco = body.gateway === 'ifthenpay' || body.gateway === 'eupago';
      const plan = PLANS.find((p) => p.id === body.planId) ?? PLANS[0];
      return {
        subscriptionId: `sub-checkout-${crypto.randomUUID().slice(0, 8)}`,
        status: 'PENDING',
        checkoutUrl: isMultibanco ? null : 'https://checkout.stripe.com/mock/session',
        paymentReference: isMultibanco
          ? { entity: '21123', reference: String(100000 + Math.floor(Math.random() * 899999)), amount: plan.price }
          : null,
      };
    });
  },
};
