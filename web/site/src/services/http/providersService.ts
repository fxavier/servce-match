import { throwProblem } from '../../lib/problem';
import type { ProviderProfile } from '../domainTypes';
import { api } from '../http';
import type { ProvidersService } from '../interfaces';
import type { ProviderSummary } from '../types';
import { notImplementedInContract } from './notImplemented';

/**
 * O contrato só devolve `ProviderSummary` (pesquisa/propostas) — sem bio,
 * portfólio, zonas ou distribuição de estrelas (gap #1, ver domainTypes.ts).
 * Degrada com defaults vazios em vez de inventar dados.
 */
function toProfile(summary: ProviderSummary): ProviderProfile {
  return {
    ...summary,
    bio: '',
    categoryNames: [],
    zones: [],
    location: { lat: 38.7223, lon: -9.1393 },
    ratingDistribution: { 5: 0, 4: 0, 3: 0, 2: 0, 1: 0 },
    portfolioImageUrls: [],
    memberSince: new Date().toISOString(),
  };
}

export const providersServiceHttp: ProvidersService = {
  async search(params) {
    const { data, error } = await api.GET('/v1/search/providers', {
      params: {
        query: {
          categoryId: params.categoryId,
          lat: params.lat,
          lon: params.lon,
          regionCode: params.regionCode,
          q: params.q,
          limit: params.limit,
          cursor: params.cursor,
        },
      },
    });
    if (error) throwProblem(error);
    return { items: data.items.map(toProfile), page: data.page };
  },
  get() {
    // Gap #1 — sem GET /v1/providers/{id} no contrato.
    return notImplementedInContract('perfil público detalhado do prestador');
  },
  async featured(limit = 6) {
    const { data, error } = await api.GET('/v1/search/providers', { params: { query: { limit } } });
    if (error) throwProblem(error);
    return data.items.map(toProfile);
  },
};
