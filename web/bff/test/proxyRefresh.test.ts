import { afterEach, describe, expect, it, vi } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { testConfig } from './testConfig.js';
import { createTestOidcSetup, tokenSuccessResponse } from './testOidc.js';
import { SessionStore } from '../src/session.js';

describe('proxy /api/** — renovação silenciosa do access_token (ADR-0012, proteção contra renovações concorrentes)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renova quando o access_token está perto de expirar, e dois pedidos concorrentes partilham a MESMA renovação', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const sessions = new SessionStore(config.session.absoluteTtlSeconds);
    const app = createApp({ config, oidcConfig: oidc.oidcConfig, sessions });

    const accessToken = oidc.fakeAccessToken('user-refresh', ['CUSTOMER']);
    // expiresInSeconds baixo o suficiente para cair dentro da margem de 30s de `isExpiringSoon`.
    const session = sessions.create({ accessToken, refreshToken: 'refresh-original', expiresInSeconds: 5 });

    let refreshCalls = 0;
    oidc.setGrantHandler('refresh_token', () => {
      refreshCalls += 1;
      const rotatedAccessToken = oidc.fakeAccessToken('user-refresh', ['CUSTOMER']);
      return tokenSuccessResponse({ access_token: rotatedAccessToken, refresh_token: 'refresh-rotated' });
    });

    // Uma `Response` só pode ter o corpo lido uma vez — cada chamada devolve
    // uma instância nova, para os dois pedidos concorrentes não colidirem a
    // ler o mesmo corpo já consumido.
    const backendFetch = vi.fn().mockImplementation(
      async () =>
        new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'content-type': 'application/json' } }),
    );
    vi.stubGlobal('fetch', backendFetch);

    const sessionCookie = `sm_sid=${session.id}`;

    const [res1, res2] = await Promise.all([
      request(app).get('/api/v1/categories').set('Cookie', [sessionCookie]),
      request(app).get('/api/v1/categories').set('Cookie', [sessionCookie]),
    ]);

    expect(res1.status).toBe(200);
    expect(res2.status).toBe(200);
    // Sem deduplicação, dois pedidos concorrentes com o access_token quase a
    // expirar despoletariam DUAS chamadas a refreshTokenGrant com o MESMO
    // refresh_token — a segunda falharia sempre que o Keycloak rode o
    // refresh_token na resposta (comportamento por omissão do Keycloak).
    expect(refreshCalls).toBe(1);

    const updated = sessions.get(session.id);
    expect(updated?.refreshToken).toBe('refresh-rotated');
  });

  it('sessão sem refresh_token e access_token expirado: 401, não tenta renovar', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const sessions = new SessionStore(config.session.absoluteTtlSeconds);
    const app = createApp({ config, oidcConfig: oidc.oidcConfig, sessions });

    const accessToken = oidc.fakeAccessToken('user-no-refresh', ['CUSTOMER']);
    const session = sessions.create({ accessToken, expiresInSeconds: 1 });

    const res = await request(app).get('/api/v1/categories').set('Cookie', [`sm_sid=${session.id}`]);
    expect(res.status).toBe(401);
  });
});
