import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { testConfig } from './testConfig.js';
import { createTestOidcSetup } from './testOidc.js';

function pickCookie(res: request.Response, name: string): string | undefined {
  const raw = res.headers['set-cookie'] as unknown as string[] | undefined;
  const found = raw?.find((c) => c.startsWith(`${name}=`));
  return found?.split(';')[0];
}

async function buildTestApp() {
  const oidc = await createTestOidcSetup();
  const config = testConfig();
  const app = createApp({ config, oidcConfig: oidc.oidcConfig });
  return { app, config, ...oidc };
}

describe('BFF — sessão e CSRF (ADR-0002)', () => {
  it('GET /auth/me sem cookie devolve authenticated:false', async () => {
    const { app } = await buildTestApp();
    const res = await request(app).get('/auth/me');
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ authenticated: false });
  });

  it('GET /api/* sem sessão devolve 401 problem+json (caso de erro) — endpoint protegido, não público', async () => {
    const { app } = await buildTestApp();
    // /v1/requests NÃO tem `security: []` no contrato (ao contrário de
    // /v1/categories, que é público desde a correção do defeito da homepage
    // — ver test/publicEndpoints.test.ts).
    const res = await request(app).get('/api/v1/requests');
    expect(res.status).toBe(401);
    expect(res.headers['content-type']).toContain('application/problem+json');
    expect(res.body.type).toBe('https://errors.servimatch.pt/unauthenticated');
  });

  it('POST /api/* sem token CSRF devolve 403 problem+json (caso de erro)', async () => {
    const { app } = await buildTestApp();
    const res = await request(app).post('/api/v1/requests').send({});
    expect(res.status).toBe(403);
    expect(res.headers['content-type']).toContain('application/problem+json');
    expect(res.body.type).toBe('https://errors.servimatch.pt/csrf-rejected');
  });
});

describe('BFF — fluxo Authorization Code + PKCE ponta a ponta', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('login → callback → sessão → chamada autenticada ao backend → logout', async () => {
    const { app, config, signIdToken, fakeAccessToken, queueTokenResponse, revokedTokens } =
      await buildTestApp();

    // 1) /auth/login inicia o Authorization Code + PKCE (nunca implicit flow).
    const loginRes = await request(app).get('/auth/login?returnTo=/requests/new');
    expect(loginRes.status).toBe(302);
    const authorizationUrl = new URL(loginRes.headers.location);
    expect(authorizationUrl.origin + authorizationUrl.pathname).toBe(
      'https://kc.test/realms/servimatch/protocol/openid-connect/auth',
    );
    expect(authorizationUrl.searchParams.get('code_challenge_method')).toBe('S256');
    expect(authorizationUrl.searchParams.get('code_challenge')).toBeTruthy();
    const state = authorizationUrl.searchParams.get('state');
    const nonce = authorizationUrl.searchParams.get('nonce');
    expect(state).toBeTruthy();
    expect(nonce).toBeTruthy();

    const oidcTransitCookie = pickCookie(loginRes, 'sm_oidc');
    expect(oidcTransitCookie).toBeTruthy();

    // 2) O Keycloak "redireciona" o browser para /auth/callback com code+state.
    const idToken = await signIdToken({
      sub: 'user-123',
      email: 'customer.test@servimatch.pt',
      preferred_username: 'customer.test',
      nonce: nonce!,
    });
    const accessToken = fakeAccessToken('user-123', ['CUSTOMER'], {
      email: 'customer.test@servimatch.pt',
      preferredUsername: 'customer.test',
    });
    queueTokenResponse({
      access_token: accessToken,
      refresh_token: 'refresh-abc',
      id_token: idToken,
    });

    const callbackRes = await request(app)
      .get(`/auth/callback?code=test-auth-code&state=${state}`)
      .set('Cookie', [oidcTransitCookie!]);

    expect(callbackRes.status).toBe(302);
    expect(callbackRes.headers.location).toBe(`${config.appOrigin}/requests/new`);
    const sessionCookie = pickCookie(callbackRes, 'sm_sid');
    expect(sessionCookie).toBeTruthy();
    // O access_token real NUNCA aparece num cookie nem em corpo de resposta.
    const rawSetCookies = (callbackRes.headers['set-cookie'] as unknown as string[]).join('\n');
    expect(rawSetCookies).not.toContain(accessToken);

    // 3) A SPA consulta /auth/me com o cookie de sessão — nunca lê tokens.
    const meRes = await request(app).get('/auth/me').set('Cookie', [sessionCookie!]);
    expect(meRes.status).toBe(200);
    expect(meRes.body).toEqual({
      authenticated: true,
      user: {
        sub: 'user-123',
        email: 'customer.test@servimatch.pt',
        username: 'customer.test',
        roles: ['CUSTOMER'],
      },
    });
    const csrfCookie = pickCookie(meRes, 'sm_csrf') ?? pickCookie(loginRes, 'sm_csrf');
    const csrfToken = csrfCookie!.split('=')[1];

    // 4) Pedido de escrita ao domínio via /api — o BFF injeta o Bearer, nunca o browser.
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ id: 'req-1', status: 'DRAFT' }), {
        status: 201,
        headers: { 'content-type': 'application/json', Location: '/v1/requests/req-1' },
      }),
    );

    const createRes = await request(app)
      .post('/api/v1/requests')
      .set('Cookie', [sessionCookie!, csrfCookie!])
      .set('X-CSRF-Token', csrfToken)
      .send({ categoryId: 'cat-1', title: 'Fuga na cozinha', address: { city: 'Lisboa' } });

    expect(createRes.status).toBe(201);
    expect(createRes.body).toEqual({ id: 'req-1', status: 'DRAFT' });
    expect(createRes.headers.location).toBe('/v1/requests/req-1');

    const forwardedRequest = fetchMock.mock.calls[0][0] as string;
    const forwardedInit = fetchMock.mock.calls[0][1] as RequestInit;
    expect(forwardedRequest).toBe(`${config.backendOrigin}/v1/requests`);
    const forwardedHeaders = forwardedInit.headers as Headers;
    expect(forwardedHeaders.get('authorization')).toBe(`Bearer ${accessToken}`);

    // 5) O backend recusa por subscrição inativa — o BFF reencaminha o Problem
    // Details tal e qual, sem o reinterpretar (CLAUDE.md §4: gating é do servidor).
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          type: 'https://errors.servimatch.pt/subscription-required',
          title: 'Subscrição inativa',
          status: 403,
        }),
        { status: 403, headers: { 'content-type': 'application/problem+json' } },
      ),
    );
    const proposalRes = await request(app)
      .post('/api/v1/requests/req-1/proposals')
      .set('Cookie', [sessionCookie!, csrfCookie!])
      .set('X-CSRF-Token', csrfToken)
      .send({ price: { amountCents: 5000, currency: 'EUR' }, description: 'Vou já' });

    expect(proposalRes.status).toBe(403);
    expect(proposalRes.headers['content-type']).toContain('application/problem+json');
    expect(proposalRes.body.type).toBe('https://errors.servimatch.pt/subscription-required');

    // 6) Logout: destrói a sessão do BFF e revoga o refresh token. Contrato
    // NOVO (ADR-0012 D3): 204 sem corpo, nunca `logoutUrl` — o utilizador
    // nunca navega para o Keycloak, por isso não há URL de fim de sessão SSO
    // a devolver. Perde-se o fim de sessão SSO no Keycloak (consequência
    // aceite conscientemente pelo ADR).
    const logoutRes = await request(app)
      .post('/auth/logout')
      .set('Cookie', [sessionCookie!, csrfCookie!])
      .set('X-CSRF-Token', csrfToken);

    expect(logoutRes.status).toBe(204);
    expect(logoutRes.body).toEqual({});
    expect(revokedTokens).toContain('refresh-abc');
    const clearedSessionCookie = pickCookie(logoutRes, 'sm_sid');
    expect(clearedSessionCookie).toBe('sm_sid=');

    const meAfterLogout = await request(app).get('/auth/me').set('Cookie', [sessionCookie!]);
    expect(meAfterLogout.body).toEqual({ authenticated: false });
  });
});
