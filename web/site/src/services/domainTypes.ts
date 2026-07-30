/**
 * Tipos que a UI precisa mas que **não existem** em `docs/api/openapi.yaml`
 * hoje — lacuna de contrato reportada ao `api-contract` em vez de inventada
 * como campo de um schema gerado (CLAUDE.md §2). `ProviderProfile`,
 * `ConversationSummary`, `ReviewWithAuthor` e `BookingDetail` deixaram de
 * viver aqui: o contrato passou a defini-los (`services/types.ts`), e um
 * tipo do cliente que os redeclarasse divergiria do gerado.
 *
 * Gap restante:
 * 1. Agregados do dashboard do prestador — sem endpoint de estatísticas
 *    (`GET /v1/providers/me/stats` ou equivalente). O dashboard deriva o que
 *    consegue de `GET /v1/providers/me/requests`, `GET /v1/proposals/me` e
 *    `GET /v1/subscriptions/me`, e mostra zero em vez de inventar números.
 */
import type { Money, ProposalStatus, SubscriptionStatus } from './types';

export interface ProviderDashboardStats {
  subscriptionStatus: SubscriptionStatus | 'NONE';
  newEligibleRequests: number;
  proposalsByStatus: Record<ProposalStatus, number>;
  acceptanceRate: number;
  estimatedRevenue: Money;
  last30Days: { date: string; proposalsSent: number; proposalsAccepted: number }[];
}
