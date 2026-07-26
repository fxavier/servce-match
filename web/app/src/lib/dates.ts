/**
 * O contrato transporta datas em ISO 8601/RFC 3339 UTC (CLAUDE.md §5).
 * `Date` já as interpreta como UTC ao fazer `parse`; formata-se sempre no
 * fuso local do browser para apresentação.
 */
export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat('pt-PT', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(iso));
}

export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('pt-PT', { dateStyle: 'medium' }).format(new Date(iso));
}
