/**
 * Tipos que a UI precisa mas que **não existem** em `docs/api/openapi.yaml`
 * hoje — lacunas de contrato identificadas ao construir este site,
 * reportadas ao `api-contract` em vez de inventadas como campos de um
 * schema gerado (CLAUDE.md §2). Ficam isoladas aqui, claramente marcadas,
 * para nunca se confundirem com os tipos gerados em `services/types.ts`.
 *
 * Gaps conhecidos (ver README "O que falta ligar ao backend real"):
 * 1. Perfil público completo do prestador (bio, portfólio, zonas, distribuição
 *    de estrelas) — o contrato só tem `ProviderSummary` (usado em pesquisa e
 *    propostas), sem endpoint `GET /v1/providers/{id}`.
 * 2. Lista de conversas do utilizador — o contrato só tem
 *    `GET /v1/conversations/{conversationId}/messages`, sem
 *    `GET /v1/conversations` para popular `/conversas`.
 * 3. Estado atual da subscrição do prestador — o contrato só tem
 *    `POST /v1/subscriptions` (iniciar checkout), sem
 *    `GET /v1/subscriptions/me`.
 * 4. Perfil editável do prestador (categorias, zonas, portfólio) — sem
 *    `GET/PUT /v1/providers/me`.
 * 5. Detalhe de uma `Booking` por id — só existe `POST .../complete`.
 */
import type { GeoPoint, Money, ProposalStatus, ProviderSummary, RequestStatus, SubscriptionStatus } from './types';

export interface ProviderZone {
  regionCode: string;
  label: string;
}

export interface RatingDistribution {
  5: number;
  4: number;
  3: number;
  2: number;
  1: number;
}

export interface ProviderProfile extends ProviderSummary {
  bio: string;
  categoryNames: string[];
  zones: ProviderZone[];
  location: GeoPoint;
  ratingDistribution: RatingDistribution;
  portfolioImageUrls: string[];
  memberSince: string;
}

export interface ConversationSummary {
  id: string;
  counterpartName: string;
  counterpartAvatarSeed: string;
  lastMessagePreview: string;
  lastMessageAt: string;
  unreadCount: number;
  requestTitle?: string;
}

export interface ReviewWithAuthor {
  id: string;
  authorName: string;
  authorAvatarSeed: string;
  targetId: string;
  rating: number;
  comment: string;
  createdAt: string;
  providerResponse?: string;
}

export interface ProviderDashboardStats {
  subscriptionStatus: SubscriptionStatus;
  newEligibleRequests: number;
  proposalsByStatus: Record<ProposalStatus, number>;
  acceptanceRate: number;
  estimatedRevenue: Money;
  last30Days: { date: string; proposalsSent: number; proposalsAccepted: number }[];
}

export interface BookingSummary {
  id: string;
  proposalId: string;
  requestTitle: string;
  counterpartName: string;
  status: 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
  scheduledStart: string | null;
  completedAt: string | null;
  canReview: boolean;
}

export type { RequestStatus };
