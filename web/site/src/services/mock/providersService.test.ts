import { describe, expect, it } from 'vitest';
import { providersServiceMock } from './providersService';

describe('providersServiceMock.search — paginação por cursor', () => {
  it('percorre todas as páginas seguindo nextCursor e termina quando nextCursor é null', async () => {
    const seen = new Set<string>();
    let cursor: string | undefined;
    let pages = 0;

    do {
      const page = await providersServiceMock.search({ limit: 5, cursor });
      page.items.forEach((item) => seen.add(item.id));
      cursor = page.page.nextCursor ?? undefined;
      pages += 1;
      expect(pages).toBeLessThan(20); // salvaguarda contra loop infinito no teste
    } while (cursor);

    // 24 prestadores fixture / 5 por página -> 5 páginas.
    expect(seen.size).toBe(24);
    expect(pages).toBe(5);
  });

  it('a última página devolve nextCursor null', async () => {
    const page = await providersServiceMock.search({ limit: 100 });
    expect(page.page.nextCursor).toBeNull();
  });
});
