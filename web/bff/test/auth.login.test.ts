import { afterEach, describe, expect, it, vi } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { testConfig } from './testConfig.js';
import { createTestOidcSetup, tokenErrorResponse, tokenSuccessResponse } from './testOidc.js';
import { getCsrf, pickCookie } from './testHttp.js';

async function buildLoginTestApp(configOverrides: Parameters<typeof testConfig>[0] = {}) {
  const oidc = await createTestOidcSetup();
  const config = testConfig({ legacyOidcFlow: { enabled: false }, ...configOverrides });
  const app = createApp({ config, oidcConfig: oidc.oidcConfig });
  return { app, config, oidc };
}

describe('POST /auth/login (ADR-0012 D3, D7)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('credenciais válidas: cria sessão, cookie HttpOnly, e o corpo nunca contém tokens', async () => {
    const { app, oidc } = await buildLoginTestApp();
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);
    const accessToken = oidc.fakeAccessToken('user-abc', ['PROVIDER'], {
      email: 'provider.test@servimatch.pt',
      preferredUsername: 'provider.test',
    });
    oidc.setGrantHandler('password', () =>
      tokenSuccessResponse({ access_token: accessToken, refresh_token: 'refresh-xyz' }),
    );

    const res = await request(app)
      .post('/auth/login')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'provider.test@servimatch.pt', password: 'DevLocal#2026' });

    expect(res.status).toBe(200);
    expect(res.body).toEqual({
      authenticated: true,
      user: {
        sub: 'user-abc',
        email: 'provider.test@servimatch.pt',
        username: 'provider.test',
        roles: ['PROVIDER'],
      },
    });
    const rawSetCookies = (res.headers['set-cookie'] as unknown as string[]).join('\n');
    expect(rawSetCookies).not.toContain(accessToken);
    expect(rawSetCookies).not.toContain('refresh-xyz');
    expect(JSON.stringify(res.body)).not.toContain(accessToken);
    expect(pickCookie(res, 'sm_sid')).toBeTruthy();
  });

  it('exige CSRF (double-submit) tal como qualquer outra escrita', async () => {
    const { app } = await buildLoginTestApp();
    const res = await request(app).post('/auth/login').send({ email: 'x@example.pt', password: 'whatever' });
    expect(res.status).toBe(403);
    expect(res.body.type).toBe('https://errors.servimatch.pt/csrf-rejected');
  });

  it('400 quando faltam campos — não chega a contactar o Keycloak', async () => {
    const { app } = await buildLoginTestApp();
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);
    const res = await request(app)
      .post('/auth/login')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: '' });
    expect(res.status).toBe(400);
    expect(res.body.type).toBe('https://errors.servimatch.pt/invalid-login');
  });

  it('conta inexistente e password errada devolvem EXATAMENTE a mesma resposta (mesmo status, mesmo corpo) — ADR-0012 D7.3', async () => {
    const { app: appA, oidc: oidcA } = await buildLoginTestApp();
    oidcA.setGrantHandler('password', () => tokenErrorResponse(401, 'invalid_grant', 'User not found'));
    const csrfA = await getCsrf(appA);
    const resNonexistent = await request(appA)
      .post('/auth/login')
      .set('Cookie', [csrfA.cookie])
      .set('X-CSRF-Token', csrfA.token)
      .send({ email: 'ninguem@example.pt', password: 'seja-o-que-for' });

    const { app: appB, oidc: oidcB } = await buildLoginTestApp();
    oidcB.setGrantHandler('password', () => tokenErrorResponse(401, 'invalid_grant', 'Invalid user credentials'));
    const csrfB = await getCsrf(appB);
    const resWrongPassword = await request(appB)
      .post('/auth/login')
      .set('Cookie', [csrfB.cookie])
      .set('X-CSRF-Token', csrfB.token)
      .send({ email: 'customer.test@servimatch.pt', password: 'password-errada' });

    expect(resNonexistent.status).toBe(401);
    expect(resWrongPassword.status).toBe(401);
    expect(resNonexistent.status).toBe(resWrongPassword.status);
    expect(resNonexistent.body).toEqual(resWrongPassword.body);
    expect(resNonexistent.body).toEqual({
      type: 'https://errors.servimatch.pt/invalid-credentials',
      title: 'Credenciais inválidas.',
      status: 401,
    });
  });

  it('fixação de sessão: um novo login gera um id de sessão novo e invalida a sessão anterior do mesmo utilizador', async () => {
    const { app, oidc } = await buildLoginTestApp();
    const accessToken1 = oidc.fakeAccessToken('user-fix', ['CUSTOMER']);
    const accessToken2 = oidc.fakeAccessToken('user-fix', ['CUSTOMER']);

    const csrf = await getCsrf(app);
    oidc.setGrantHandler('password', () => tokenSuccessResponse({ access_token: accessToken1, refresh_token: 'r1' }));
    const first = await request(app)
      .post('/auth/login')
      .set('Cookie', [csrf.cookie])
      .set('X-CSRF-Token', csrf.token)
      .send({ email: 'fixacao@example.pt', password: 'DevLocal#2026' });
    const firstSessionCookie = pickCookie(first, 'sm_sid')!;
    expect(firstSessionCookie).toBeTruthy();

    oidc.setGrantHandler('password', () => tokenSuccessResponse({ access_token: accessToken2, refresh_token: 'r2' }));
    const second = await request(app)
      .post('/auth/login')
      .set('Cookie', [csrf.cookie])
      .set('X-CSRF-Token', csrf.token)
      .send({ email: 'fixacao@example.pt', password: 'DevLocal#2026' });
    const secondSessionCookie = pickCookie(second, 'sm_sid')!;
    expect(secondSessionCookie).toBeTruthy();
    expect(secondSessionCookie).not.toBe(firstSessionCookie);

    const meWithOldCookie = await request(app).get('/auth/me').set('Cookie', [firstSessionCookie]);
    expect(meWithOldCookie.body).toEqual({ authenticated: false });

    const meWithNewCookie = await request(app).get('/auth/me').set('Cookie', [secondSessionCookie]);
    expect(meWithNewCookie.body.authenticated).toBe(true);
  });

  it('rate limiting por IP e por email, antes de chamar o Keycloak', async () => {
    const { app, oidc } = await buildLoginTestApp({
      authRateLimit: { windowMs: 60_000, maxPerIp: 100, maxPerEmail: 1 },
    });
    let grantCalls = 0;
    oidc.setGrantHandler('password', () => {
      grantCalls += 1;
      return tokenErrorResponse(401, 'invalid_grant');
    });
    const csrf = await getCsrf(app);

    await request(app)
      .post('/auth/login')
      .set('Cookie', [csrf.cookie])
      .set('X-CSRF-Token', csrf.token)
      .send({ email: 'alvo@example.pt', password: 'tentativa-1' });

    const res = await request(app)
      .post('/auth/login')
      .set('Cookie', [csrf.cookie])
      .set('X-CSRF-Token', csrf.token)
      .send({ email: 'alvo@example.pt', password: 'tentativa-2' });

    expect(res.status).toBe(429);
    expect(res.headers['retry-after']).toBeTruthy();
    expect(grantCalls).toBe(1);
  });
});
