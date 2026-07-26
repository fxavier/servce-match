import { describe, expect, it, vi } from 'vitest';
import { SessionStore } from '../src/session.js';

function fakeJwt(claims: Record<string, unknown>): string {
  const header = Buffer.from(JSON.stringify({ alg: 'none' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify(claims)).toString('base64url');
  return `${header}.${payload}.sig`;
}

describe('SessionStore', () => {
  it('deriva sub e roles do access_token ao criar a sessão', () => {
    const store = new SessionStore();
    const token = fakeJwt({
      sub: 'abc-123',
      email: 'provider.test@servimatch.pt',
      preferred_username: 'provider.test',
      realm_access: { roles: ['PROVIDER'] },
      exp: Math.floor(Date.now() / 1000) + 300,
    });

    const session = store.create({ accessToken: token, expiresInSeconds: 300 });

    expect(session.user).toEqual({
      sub: 'abc-123',
      email: 'provider.test@servimatch.pt',
      username: 'provider.test',
      roles: ['PROVIDER'],
    });
    expect(store.get(session.id)).toBe(session);
  });

  it('destroy remove a sessão do store', () => {
    const store = new SessionStore();
    const session = store.create({
      accessToken: fakeJwt({ sub: 'x', exp: 0 }),
      expiresInSeconds: 300,
    });
    store.destroy(session.id);
    expect(store.get(session.id)).toBeUndefined();
  });

  it('isExpiringSoon é true dentro da margem de 30s', () => {
    vi.useFakeTimers();
    try {
      const store = new SessionStore();
      const session = store.create({
        accessToken: fakeJwt({ sub: 'x', exp: 0 }),
        expiresInSeconds: 40,
      });
      expect(store.isExpiringSoon(session)).toBe(false);
      vi.advanceTimersByTime(11_000);
      expect(store.isExpiringSoon(session)).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });
});
