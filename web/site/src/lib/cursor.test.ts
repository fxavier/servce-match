import { describe, expect, it } from 'vitest';
import { flattenPages, getNextCursorParam } from './cursor';

describe('getNextCursorParam', () => {
  it('devolve o nextCursor quando existe', () => {
    expect(getNextCursorParam({ items: [1, 2], page: { nextCursor: 'abc' } })).toBe('abc');
  });

  it('devolve undefined (não null) quando nextCursor é null — encerra o infinite scroll', () => {
    expect(getNextCursorParam({ items: [1, 2], page: { nextCursor: null } })).toBeUndefined();
  });
});

describe('flattenPages', () => {
  it('achata as páginas por ordem', () => {
    const pages = [
      { items: [1, 2], page: { nextCursor: 'a' } },
      { items: [3, 4], page: { nextCursor: null } },
    ];
    expect(flattenPages(pages)).toEqual([1, 2, 3, 4]);
  });

  it('devolve [] quando não há páginas ainda', () => {
    expect(flattenPages(undefined)).toEqual([]);
  });
});
