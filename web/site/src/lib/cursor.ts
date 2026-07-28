/** Envelope de paginação por cursor partilhado por todas as listas (CLAUDE.md §5). */
export interface PageMeta {
  nextCursor: string | null;
}

export interface CursorPage<T> {
  items: T[];
  page: PageMeta;
}

/** `getNextPageParam` genérico para `useInfiniteQuery` — nunca assume offset nem total conhecido. */
export function getNextCursorParam<T>(lastPage: CursorPage<T>): string | undefined {
  return lastPage.page.nextCursor ?? undefined;
}

export function flattenPages<T>(pages: CursorPage<T>[] | undefined): T[] {
  return pages?.flatMap((page) => page.items) ?? [];
}
