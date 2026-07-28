import type { Proposal, ProposalStatus } from '../../types';
import { CATEGORIES } from './categories';
import { PROVIDER_CATEGORY_IDS, PROVIDERS, providerById } from './providers';
import { REQUESTS } from './requests';

function hoursAgoIso(hours: number): string {
  return new Date(Date.now() - hours * 60 * 60 * 1000).toISOString();
}

function hoursFromNowIso(hours: number): string {
  return new Date(Date.now() + hours * 60 * 60 * 1000).toISOString();
}

/** Faixa de preço plausível (cêntimos) por categoria de topo, para não gerar valores absurdos. */
const PRICE_RANGE_CENTS: Record<string, [number, number]> = {
  canalizacao: [6000, 25000],
  eletricidade: [5000, 20000],
  climatizacao: [15000, 60000],
  jardinagem: [4000, 15000],
  limpezas: [3000, 12000],
  mudancas: [15000, 45000],
  'obras-remodelacoes': [200000, 900000],
  pinturas: [40000, 150000],
  serralharia: [8000, 30000],
  carpintaria: [30000, 120000],
};

function topLevelSlugOf(categoryId: string): string {
  const category = CATEGORIES.find((c) => c.id === categoryId);
  if (!category) return 'canalizacao';
  if (category.parentId === null) return category.slug;
  const parent = CATEGORIES.find((c) => c.id === category.parentId);
  return parent?.slug ?? category.slug;
}

function topLevelSlugFor(providerId: string): string {
  const categoryIds = PROVIDER_CATEGORY_IDS[providerId] ?? [];
  return categoryIds.length > 0 ? topLevelSlugOf(categoryIds[0]) : 'canalizacao';
}

// Sequência determinística em vez de Math.random() — mantém os testes estáveis.
function pseudoRandom(seed: number): number {
  const x = Math.sin(seed * 999) * 10000;
  return x - Math.floor(x);
}

function priceFor(providerId: string, seed: number): number {
  const slug = topLevelSlugFor(providerId);
  const [min, max] = PRICE_RANGE_CENTS[slug] ?? [5000, 30000];
  const raw = min + pseudoRandom(seed) * (max - min);
  return Math.round(raw / 500) * 500; // arredonda a múltiplos de 5€ — preço plausível de orçamento
}

const DESCRIPTIONS = [
  'Incluí deslocação e mão de obra. Materiais faturados à parte, com fatura detalhada.',
  'Orçamento fechado, sem surpresas — se aparecer algo extra, aviso antes de avançar.',
  'Posso começar esta semana. Preço inclui garantia de 12 meses sobre o trabalho.',
  'Valor inclui limpeza do espaço no final do serviço.',
  'Trabalho com equipa de 2 pessoas, o que permite cumprir este prazo.',
  'Preço válido para o material standard; se preferir gama superior, ajusto o orçamento.',
];

let cursor = 0;
function nextDescription(): string {
  const value = DESCRIPTIONS[cursor % DESCRIPTIONS.length];
  cursor += 1;
  return value;
}

function statusPlanFor(requestStatus: string, index: number, total: number): ProposalStatus {
  if (requestStatus === 'CONFIRMED' || requestStatus === 'IN_PROGRESS' || requestStatus === 'COMPLETED') {
    return index === 0 ? 'ACCEPTED' : 'SUPERSEDED';
  }
  if (requestStatus === 'CANCELLED') {
    return index % 2 === 0 ? 'CANCELLED' : 'EXPIRED';
  }
  // DRAFT/PUBLISHED/IN_NEGOTIATION: maioria SENT, uma ou outra EXPIRED/REJECTED para variedade.
  if (total > 3 && index === total - 1) return 'EXPIRED';
  if (total > 4 && index === total - 2) return 'REJECTED';
  return 'SENT';
}

function providersFor(count: number, offset: number): typeof PROVIDERS {
  const result: typeof PROVIDERS = [];
  for (let i = 0; i < count; i += 1) {
    result.push(PROVIDERS[(offset + i) % PROVIDERS.length]);
  }
  return result;
}

let globalIndex = 0;

export const PROPOSALS: Proposal[] = REQUESTS.flatMap((request, requestIndex) => {
  const count = request.proposalCount ?? 0;
  const providers = providersFor(count, requestIndex * 3);
  return providers.map((provider, index) => {
    globalIndex += 1;
    const seed = globalIndex;
    const status = statusPlanFor(request.status, index, count);
    const createdHoursAgo = 2 + index * 4 + (requestIndex % 5);
    const price = priceFor(provider.id, seed);
    const leadTimeDays = 1 + Math.round(pseudoRandom(seed + 1) * 13);
    return {
      id: `pr-${request.id}-${provider.id}`,
      requestId: request.id,
      providerId: provider.id,
      providerSummary: {
        id: provider.id,
        displayName: provider.displayName,
        headline: provider.headline,
        companyName: provider.companyName ?? null,
        ratingAvg: provider.ratingAvg,
        ratingCount: provider.ratingCount,
        verified: provider.verified,
        premiumBadge: provider.premiumBadge,
        avatarUrl: null,
      },
      price: { amountCents: price, currency: 'EUR' },
      description: nextDescription(),
      leadTimeDays,
      validUntil: status === 'SENT' ? hoursFromNowIso(24 + index * 12) : status === 'EXPIRED' ? hoursAgoIso(2) : null,
      status,
      createdAt: hoursAgoIso(createdHoursAgo),
    } satisfies Proposal;
  });
});

export function proposalsForRequest(requestId: string): Proposal[] {
  return PROPOSALS.filter((proposal) => proposal.requestId === requestId);
}

export function proposalById(id: string): Proposal | undefined {
  return PROPOSALS.find((proposal) => proposal.id === id);
}

export function proposalsForProvider(providerId: string): Proposal[] {
  return PROPOSALS.filter((proposal) => proposal.providerId === providerId);
}

export { providerById };
