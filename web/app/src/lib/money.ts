import type { components } from '../api/generated/schema';

type Money = components['schemas']['Money'];

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
