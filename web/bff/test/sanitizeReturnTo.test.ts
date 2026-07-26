import { describe, expect, it } from 'vitest';
import { sanitizeReturnTo } from '../src/routes/auth.js';

describe('sanitizeReturnTo (proteção contra open redirect)', () => {
  it('aceita caminhos internos', () => {
    expect(sanitizeReturnTo('/requests/new')).toBe('/requests/new');
    expect(sanitizeReturnTo('/requests/abc?x=1')).toBe('/requests/abc?x=1');
  });

  it('rejeita ausência de valor ou valor não-string, devolvendo "/"', () => {
    expect(sanitizeReturnTo(undefined)).toBe('/');
    expect(sanitizeReturnTo('')).toBe('/');
    expect(sanitizeReturnTo(42)).toBe('/');
  });

  it('rejeita URLs absolutas e protocol-relative', () => {
    expect(sanitizeReturnTo('https://evil.example/')).toBe('/');
    expect(sanitizeReturnTo('//evil.example/')).toBe('/');
  });

  it('rejeita o bypass por backslash que os browsers normalizam para "//"', () => {
    // Classe de vulnerabilidade adjacente ao CVE-2025-68470: browsers tratam
    // `\` como `/` em URLs, por isso isto vira `//evil.example` na prática.
    expect(sanitizeReturnTo('/\\evil.example')).toBe('/');
    expect(sanitizeReturnTo('\\/evil.example')).toBe('/');
    expect(sanitizeReturnTo('\\\\evil.example')).toBe('/');
  });
});
