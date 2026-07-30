import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { urlOf } from '../../test/mockFetch';
import { providersServiceHttp } from './providersService';

/**
 * Substitui `services/mock/providersService.test.ts` (removido com a
 * camada de mocks — ADR-0013 D7): a mesma lógica de paginação por cursor,
 * agora exercida sobre a camada HTTP real com `fetch` mockado, para não
 * perder cobertura.
 */

const TOTAL = 24;
const PAGE_SIZE = 5;

function makeProvider(id: number) {
  return {
    id: `prov-${id}`,
    displayName: `Prestador ${id}`,
    ratingAvg: 4.5,
    ratingCount: 3,
  };
}

function installFetchMock() {
  const all = Array.from({ length: TOTAL }, (_, index) => makeProvider(index));
  const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const url = new URL(urlOf(input), 'http://localhost');
    const cursor = url.searchParams.get('cursor');
    const limit = Number(url.searchParams.get('limit') ?? PAGE_SIZE);
    const start = cursor ? Number(cursor) : 0;
    const items = all.slice(start, start + limit);
    const nextCursor = start + limit < TOTAL ? String(start + limit) : null;
    return new Response(JSON.stringify({ items, page: { nextCursor } }), {
      status: 200,
      headers: { 'content-type': 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fetchMock);
}

describe('providersServiceHttp.search — paginação por cursor', () => {
  beforeEach(() => {
    installFetchMock();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('percorre todas as páginas seguindo nextCursor e termina quando nextCursor é null', async () => {
    const seen = new Set<string>();
    let cursor: string | undefined;
    let pages = 0;

    do {
      const page = await providersServiceHttp.search({ limit: PAGE_SIZE, cursor });
      page.items.forEach((item) => seen.add(item.id));
      cursor = page.page.nextCursor ?? undefined;
      pages += 1;
      expect(pages).toBeLessThan(20); // salvaguarda contra loop infinito no teste
    } while (cursor);

    expect(seen.size).toBe(TOTAL);
    expect(pages).toBe(Math.ceil(TOTAL / PAGE_SIZE));
  });

  it('a última página devolve nextCursor null', async () => {
    const page = await providersServiceHttp.search({ limit: 100 });
    expect(page.page.nextCursor).toBeNull();
  });
});
