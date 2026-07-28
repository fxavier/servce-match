import { describe, expect, it } from 'vitest';
import { centsToEurosInput, eurosToCents, formatMoney } from './money';

// `Intl.NumberFormat('pt-PT', { style: 'currency' })` usa espaço insecável
// (U+00A0) antes do símbolo — não um espaço normal. Normalizamos qualquer
// espaço em branco para " " antes de comparar strings literais no teste.
function normalizeSpaces(value: string): string {
  return value.replace(/\s/gu, ' ');
}

describe('formatMoney', () => {
  it('formata cêntimos em euros pt-PT', () => {
    expect(normalizeSpaces(formatMoney({ amountCents: 1990, currency: 'EUR' }))).toBe('19,90 €');
    expect(normalizeSpaces(formatMoney({ amountCents: 6990, currency: 'EUR' }))).toBe('69,90 €');
  });

  it('nunca faz aritmética de vírgula flutuante sobre o valor — só divide para apresentar', () => {
    // 4500 cêntimos -> 45,00€ exato, sem erro de ponto flutuante acumulado.
    expect(normalizeSpaces(formatMoney({ amountCents: 4500, currency: 'EUR' }))).toBe('45,00 €');
    expect(normalizeSpaces(formatMoney({ amountCents: 1, currency: 'EUR' }))).toBe('0,01 €');
  });

  it('respeita a moeda declarada', () => {
    expect(formatMoney({ amountCents: 100, currency: 'USD' })).toContain('US$');
  });
});

describe('eurosToCents', () => {
  it('converte texto de input em cêntimos inteiros (convenção pt-PT: vírgula decimal, ponto de milhares)', () => {
    expect(eurosToCents('39,90')).toBe(3990);
    expect(eurosToCents('100')).toBe(10000);
    expect(eurosToCents('1.234,50')).toBe(123450);
  });

  it('devolve undefined para texto inválido ou vazio', () => {
    expect(eurosToCents('')).toBeUndefined();
    expect(eurosToCents('abc')).toBeUndefined();
    expect(eurosToCents('12,999')).toBeUndefined();
  });
});

describe('centsToEurosInput', () => {
  it('formata cêntimos de volta para o formato de input', () => {
    expect(centsToEurosInput(3990)).toBe('39,90');
    expect(centsToEurosInput(100)).toBe('1,00');
  });
});
