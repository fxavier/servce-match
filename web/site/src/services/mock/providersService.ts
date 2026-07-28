import type { ProviderProfile } from '../domainTypes';
import type { ProvidersService } from '../interfaces';
import { REGIONS } from '../../constants/regions';
import { PROVIDER_CATEGORY_IDS, PROVIDERS, providerById } from './fixtures/providers';
import { withLatency } from './latency';
import { throwProblem } from './mockProblem';

const PAGE_SIZE_DEFAULT = 12;

function haversineKm(a: { lat: number; lon: number }, b: { lat: number; lon: number }): number {
  const R = 6371;
  const dLat = ((b.lat - a.lat) * Math.PI) / 180;
  const dLon = ((b.lon - a.lon) * Math.PI) / 180;
  const lat1 = (a.lat * Math.PI) / 180;
  const lat2 = (b.lat * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

function matches(provider: ProviderProfile, params: Parameters<ProvidersService['search']>[0]): boolean {
  if (params.categoryId) {
    const categoryIds = PROVIDER_CATEGORY_IDS[provider.id] ?? [];
    if (!categoryIds.includes(params.categoryId)) return false;
  }
  if (params.regionCode && !provider.zones.some((zone) => zone.regionCode === params.regionCode)) return false;
  if (params.minRating && provider.ratingAvg < params.minRating) return false;
  if (params.verifiedOnly && !provider.verified) return false;
  if (params.premiumOnly && !provider.premiumBadge) return false;
  if (params.q) {
    const needle = params.q.toLowerCase();
    const haystack = `${provider.displayName} ${provider.headline} ${provider.companyName ?? ''}`.toLowerCase();
    if (!haystack.includes(needle)) return false;
  }
  return true;
}

function sortProviders(items: ProviderProfile[], params: Parameters<ProvidersService['search']>[0]): ProviderProfile[] {
  const sorted = [...items];
  if (params.sort === 'rating') {
    sorted.sort((a, b) => b.ratingAvg - a.ratingAvg);
  } else if (params.sort === 'distance' && params.lat !== undefined && params.lon !== undefined) {
    const origin = { lat: params.lat, lon: params.lon };
    sorted.sort((a, b) => haversineKm(origin, a.location) - haversineKm(origin, b.location));
  } else {
    // relevância: premium primeiro, depois rating (§7 rankingBoost do plano Premium).
    sorted.sort((a, b) => Number(b.premiumBadge) - Number(a.premiumBadge) || b.ratingAvg - a.ratingAvg);
  }
  return sorted;
}

export const providersServiceMock: ProvidersService = {
  search(params) {
    return withLatency(() => {
      const filtered = sortProviders(PROVIDERS.filter((provider) => matches(provider, params)), params);
      const limit = params.limit ?? PAGE_SIZE_DEFAULT;
      const offset = params.cursor ? Number(params.cursor) : 0;
      const page = filtered.slice(offset, offset + limit);
      const nextOffset = offset + limit;
      return {
        items: page,
        page: { nextCursor: nextOffset < filtered.length ? String(nextOffset) : null },
      };
    });
  },
  get(id) {
    return withLatency(() => {
      const provider = providerById(id);
      if (!provider) {
        throwProblem({ type: 'https://errors.servimatch.pt/not-found', title: 'Prestador não encontrado.', status: 404 });
      }
      return provider;
    });
  },
  featured(limit = 6) {
    return withLatency(() => sortProviders(PROVIDERS, { sort: 'relevance' }).slice(0, limit));
  },
};

export { REGIONS };
