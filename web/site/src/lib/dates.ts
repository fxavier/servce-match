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

const RELATIVE_UNITS: { unit: Intl.RelativeTimeFormatUnit; ms: number }[] = [
  { unit: 'year', ms: 365 * 24 * 60 * 60 * 1000 },
  { unit: 'month', ms: 30 * 24 * 60 * 60 * 1000 },
  { unit: 'day', ms: 24 * 60 * 60 * 1000 },
  { unit: 'hour', ms: 60 * 60 * 1000 },
  { unit: 'minute', ms: 60 * 1000 },
];

const relativeFormatter = new Intl.RelativeTimeFormat('pt-PT', { numeric: 'auto' });

/** "há 12 min", "há 3 h", "ontem" — usado nos eyebrows Plex Mono de metadados (§5.2). */
export function formatRelativeTime(iso: string, now: Date = new Date()): string {
  const diffMs = new Date(iso).getTime() - now.getTime();
  for (const { unit, ms } of RELATIVE_UNITS) {
    if (Math.abs(diffMs) >= ms || unit === 'minute') {
      return relativeFormatter.format(Math.round(diffMs / ms), unit);
    }
  }
  return relativeFormatter.format(0, 'minute');
}

/** "expira em 3 d 4 h" — contagem decrescente para validade de propostas. */
export function formatCountdown(iso: string, now: Date = new Date()): string {
  const diffMs = new Date(iso).getTime() - now.getTime();
  if (diffMs <= 0) return 'expirado';
  const days = Math.floor(diffMs / (24 * 60 * 60 * 1000));
  const hours = Math.floor((diffMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000));
  if (days > 0) return `expira em ${days} d ${hours} h`;
  const minutes = Math.floor((diffMs % (60 * 60 * 1000)) / (60 * 1000));
  if (hours > 0) return `expira em ${hours} h ${minutes} min`;
  return `expira em ${minutes} min`;
}
