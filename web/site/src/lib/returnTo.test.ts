import { describe, expect, it } from 'vitest';
import { sanitizeReturnTo } from './returnTo';

/** Mesma bateria de testes que web/bff/test/sanitizeReturnTo.test.ts — a mesma
 * proteção replicada no cliente (ver comentário em returnTo.ts). */
describe('sanitizeReturnTo (proteção contra open redirect)', () => {
  it('aceita caminhos internos', () => {
    expect(sanitizeReturnTo('/pedidos/novo')).toBe('/pedidos/novo');
    expect(sanitizeReturnTo('/pedidos/abc?x=1')).toBe('/pedidos/abc?x=1');
  });

  it('rejeita ausência de valor, devolvendo "/"', () => {
    expect(sanitizeReturnTo(undefined)).toBe('/');
    expect(sanitizeReturnTo(null)).toBe('/');
    expect(sanitizeReturnTo('')).toBe('/');
  });

  it('rejeita URLs absolutas e protocol-relative', () => {
    expect(sanitizeReturnTo('https://evil.example/')).toBe('/');
    expect(sanitizeReturnTo('//evil.example/')).toBe('/');
  });

  it('rejeita o bypass por backslash que os browsers normalizam para "//"', () => {
    expect(sanitizeReturnTo('/\\evil.example')).toBe('/');
    expect(sanitizeReturnTo('\\/evil.example')).toBe('/');
    expect(sanitizeReturnTo('\\\\evil.example')).toBe('/');
  });
});
