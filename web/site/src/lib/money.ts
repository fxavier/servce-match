import type { Money } from '../services/types';

const formatterCache = new Map<string, Intl.NumberFormat>();

function formatterFor(currency: string): Intl.NumberFormat {
  let formatter = formatterCache.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat('pt-PT', { style: 'currency', currency });
    formatterCache.set(currency, formatter);
  }
  return formatter;
}

/**
 * Formata dinheiro a partir de `amountCents` (inteiro) + `currency`
 * (CLAUDE.md §5). A única operação em vírgula flutuante é esta divisão por
 * 100 para *apresentação*; nunca se soma, subtrai ou compara dinheiro em
 * `number` — isso faz-se sempre sobre os cêntimos inteiros.
 */
export function formatMoney(money: Money): string {
  return formatterFor(money.currency).format(money.amountCents / 100);
}

/** Converte um valor introduzido em euros (string do input, ex. "39,90") para cêntimos inteiros. */
export function eurosToCents(input: string): number | undefined {
  const normalized = input.trim().replace(/\s|€/g, '').replace(/\./g, '').replace(',', '.');
  if (normalized === '') return undefined;
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return undefined;
  const [whole, fraction = ''] = normalized.split('.');
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, '0').slice(0, 2));
  return Number.isFinite(cents) ? cents : undefined;
}

/** Formata cêntimos como euros para reintroduzir num input mascarado (ex. `3990` -> "39,90"). */
export function centsToEurosInput(cents: number): string {
  return (cents / 100).toFixed(2).replace('.', ',');
}
