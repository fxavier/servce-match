import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { ProvidersService } from '../interfaces';

/**
 * `GET /v1/search/providers` devolve `ProviderSummary` — sem `bio`,
 * `location`, zonas nem portfólio (só `GET /v1/providers/{id}`/`me` têm
 * isso). Devolver `ProviderSummary` tal como veio, em vez de fabricar um
 * `ProviderProfile` com valores por omissão (ex. localização fixa em
 * Lisboa) — um marcador de mapa inventado é pior do que não ter mapa.
 */
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
    return data;
  },
  async get(id) {
    const { data, error } = await api.GET('/v1/providers/{providerId}', { params: { path: { providerId: id } } });
    if (error) throwProblem(error);
    return data;
  },
  async featured(limit = 6) {
    const { data, error } = await api.GET('/v1/search/providers', { params: { query: { limit } } });
    if (error) throwProblem(error);
    return data.items;
  },
  async getMine() {
    const { data, error } = await api.GET('/v1/providers/me');
    if (error) throwProblem(error);
    return data;
  },
  async updateMine(body) {
    const { data, error } = await api.PUT('/v1/providers/me', { body });
    if (error) throwProblem(error);
    return data;
  },
};
