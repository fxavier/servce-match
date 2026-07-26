import { describe, expect, it } from 'vitest';
import { formatMoney } from './money';

// Intl.NumberFormat('pt-PT', { style: 'currency' }) usa um espaço
// insecável (U+00A0) antes do símbolo — não um espaço normal (U+0020).
const NBSP = '\u00A0';

describe('formatMoney', () => {
  it('formata cêntimos em EUR no formato pt-PT', () => {
    expect(formatMoney({ amountCents: 4500, currency: 'EUR' })).toBe(`45,00${NBSP}€`);
  });

  it('formata valores com cêntimos não-zero', () => {
    expect(formatMoney({ amountCents: 1999, currency: 'EUR' })).toBe(`19,99${NBSP}€`);
  });

  it('formata zero corretamente', () => {
    expect(formatMoney({ amountCents: 0, currency: 'EUR' })).toBe(`0,00${NBSP}€`);
  });
});
