import { describe, expect, it } from 'vitest';
import { signPayload, verifyPayload } from '../src/signedCookie.js';

describe('signedCookie', () => {
  it('verifica um payload assinado com o mesmo segredo', () => {
    const token = signPayload({ state: 'abc', nonce: 'def' }, 'secret-1');
    expect(verifyPayload<{ state: string; nonce: string }>(token, 'secret-1')).toEqual({
      state: 'abc',
      nonce: 'def',
    });
  });

  it('rejeita um payload assinado com outro segredo (cookie adulterado)', () => {
    const token = signPayload({ state: 'abc' }, 'secret-1');
    expect(verifyPayload(token, 'secret-2')).toBeUndefined();
  });

  it('rejeita um token malformado', () => {
    expect(verifyPayload('not-a-valid-token', 'secret-1')).toBeUndefined();
  });
});
