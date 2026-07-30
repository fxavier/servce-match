import { afterEach, describe, expect, it, vi } from 'vitest';
import { SessionStore } from '../src/session.js';

function fakeJwt(claims: Record<string, unknown>): string {
  const header = Buffer.from(JSON.stringify({ alg: 'none' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify(claims)).toString('base64url');
  return `${header}.${payload}.sig`;
}

describe('SessionStore — ciclo de vida da sessão (ADR-0012)', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('TTL absoluto: expira a sessão mesmo com access_token de vida longa, e nunca é estendido por update()', () => {
    vi.useFakeTimers();
    const store = new SessionStore(60); // 60s de TTL absoluto
    const session = store.create({
      accessToken: fakeJwt({ sub: 'abs-1', exp: Math.floor(Date.now() / 1000) + 999_999 }),
      expiresInSeconds: 999_999, // access token "nunca" expira sozinho
    });

    // Uma renovação de access/refresh token NÃO deve estender o TTL absoluto.
    store.update(session.id, { accessToken: fakeJwt({ sub: 'abs-1', exp: 0 }), accessTokenExpiresAt: Date.now() + 999_999_000 });
    expect(store.get(session.id)?.absoluteExpiresAt).toBe(session.absoluteExpiresAt);

    vi.advanceTimersByTime(59_000);
    expect(store.get(session.id)).toBeTruthy();

    vi.advanceTimersByTime(2_000); // ultrapassa os 60s
    expect(store.get(session.id)).toBeUndefined();
  });

  it('sweepExpired remove sessões cujo TTL absoluto passou, sem depender de get() ser chamado', () => {
    vi.useFakeTimers();
    const store = new SessionStore(60);
    const session = store.create({ accessToken: fakeJwt({ sub: 'sweep-1', exp: 0 }), expiresInSeconds: 999_999 });

    vi.advanceTimersByTime(61_000);
    const swept = store.sweepExpired();
    expect(swept).toBe(1);

    // Confirma que já não está lá por baixo (não só que get() a esconderia).
    vi.useRealTimers();
    expect(store.get(session.id)).toBeUndefined();
  });

  it('fixação de sessão: create() gera sempre um id novo e destrói a sessão anterior do mesmo utilizador (sub)', () => {
    const store = new SessionStore();
    const first = store.create({ accessToken: fakeJwt({ sub: 'user-1', exp: 0 }), expiresInSeconds: 300 });
    const second = store.create({ accessToken: fakeJwt({ sub: 'user-1', exp: 0 }), expiresInSeconds: 300 });

    expect(second.id).not.toBe(first.id);
    expect(store.get(first.id)).toBeUndefined();
    expect(store.get(second.id)).toBeTruthy();
  });

  it('fixação de sessão não afeta utilizadores diferentes', () => {
    const store = new SessionStore();
    const userA = store.create({ accessToken: fakeJwt({ sub: 'user-a', exp: 0 }), expiresInSeconds: 300 });
    const userB = store.create({ accessToken: fakeJwt({ sub: 'user-b', exp: 0 }), expiresInSeconds: 300 });

    expect(store.get(userA.id)).toBeTruthy();
    expect(store.get(userB.id)).toBeTruthy();
  });

  it('destroyByUser invalida a sessão ativa de um utilizador por sub', () => {
    const store = new SessionStore();
    const session = store.create({ accessToken: fakeJwt({ sub: 'user-logout-all', exp: 0 }), expiresInSeconds: 300 });
    store.destroyByUser('user-logout-all');
    expect(store.get(session.id)).toBeUndefined();
  });

  it('refreshOnce deduplica renovações concorrentes do mesmo id de sessão (chama o refresher só uma vez)', async () => {
    const store = new SessionStore();
    const session = store.create({ accessToken: fakeJwt({ sub: 'user-concurrent', exp: 0 }), expiresInSeconds: 300 });

    let refresherCalls = 0;
    let resolveRefresh!: (value: { accessToken: string; accessTokenExpiresAt: number }) => void;
    const refresherResult = new Promise<{ accessToken: string; accessTokenExpiresAt: number }>((resolve) => {
      resolveRefresh = resolve;
    });

    const refresher = vi.fn(async () => {
      refresherCalls += 1;
      return refresherResult;
    });

    // Dois pedidos "concorrentes" (mesmo id de sessão) despoletam a renovação ao mesmo tempo.
    const p1 = store.refreshOnce(session.id, refresher);
    const p2 = store.refreshOnce(session.id, refresher);

    expect(refresherCalls).toBe(1); // não duas chamadas a refreshTokenGrant com o mesmo refresh_token

    resolveRefresh({ accessToken: 'new-access-token', accessTokenExpiresAt: Date.now() + 300_000 });
    const [r1, r2] = await Promise.all([p1, p2]);

    expect(r1?.accessToken).toBe('new-access-token');
    expect(r2?.accessToken).toBe('new-access-token');
    expect(r1?.id).toBe(session.id);

    // Uma renovação SEGUINTE (não concorrente com a primeira) volta a chamar o refresher normalmente.
    const refresher2 = vi.fn(async () => ({ accessToken: 'yet-another-token', accessTokenExpiresAt: Date.now() + 300_000 }));
    await store.refreshOnce(session.id, refresher2);
    expect(refresher2).toHaveBeenCalledTimes(1);
  });

  it('refreshOnce devolve undefined para uma sessão que já não existe, sem chamar o refresher', async () => {
    const store = new SessionStore();
    const refresher = vi.fn();
    const result = await store.refreshOnce('sessao-inexistente', refresher);
    expect(result).toBeUndefined();
    expect(refresher).not.toHaveBeenCalled();
  });
});
