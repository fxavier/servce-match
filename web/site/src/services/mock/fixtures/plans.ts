import type { SubscriptionPlan } from '../../types';

/**
 * Planos reais confirmados contra `GET /v1/subscription-plans` no backend
 * local a correr (`curl localhost:8080/v1/subscription-plans`) — mesmos
 * `id`, preços e limites do seed (prioridade #5 do briefing).
 */
export const PLANS: SubscriptionPlan[] = [
  {
    id: 'f5e969fb-50c2-46ce-b0a8-6a5105f32159',
    code: 'starter',
    name: 'Starter',
    price: { amountCents: 1990, currency: 'EUR' },
    interval: 'MONTHLY',
    maxCategories: 3,
    maxAreas: 2,
    rankingBoost: 0,
    hasBadge: false,
    active: true,
  },
  {
    id: '514ba56d-3624-427f-9c4e-151c61f7c886',
    code: 'professional',
    name: 'Professional',
    price: { amountCents: 3990, currency: 'EUR' },
    interval: 'MONTHLY',
    maxCategories: null,
    maxAreas: null,
    rankingBoost: 0,
    hasBadge: false,
    active: true,
  },
  {
    id: '36c6e2be-0b21-41ce-a711-3ffa4a5c2a70',
    code: 'premium',
    name: 'Premium',
    price: { amountCents: 6990, currency: 'EUR' },
    interval: 'MONTHLY',
    maxCategories: null,
    maxAreas: null,
    rankingBoost: 10,
    hasBadge: true,
    active: true,
  },
];
