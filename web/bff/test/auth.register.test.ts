import { afterEach, describe, expect, it, vi } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { testConfig } from './testConfig.js';
import { createTestOidcSetup, tokenErrorResponse, tokenSuccessResponse } from './testOidc.js';
import { createKeycloakAdminMock } from './testKeycloakAdmin.js';
import { getCsrf, pickCookie } from './testHttp.js';

async function buildRegisterTestApp(configOverrides: Parameters<typeof testConfig>[0] = {}) {
  const oidc = await createTestOidcSetup();
  const config = testConfig({ legacyOidcFlow: { enabled: false }, ...configOverrides });
  const admin = createKeycloakAdminMock(config.keycloak.adminApiBaseUrl);
  vi.stubGlobal('fetch', admin.fetchMock);
  const clientCredentialsCalls = { count: 0 };
  oidc.setGrantHandler('client_credentials', () => {
    clientCredentialsCalls.count += 1;
    return tokenSuccessResponse({ access_token: `admin-service-account-token-${clientCredentialsCalls.count}` });
  });
  const app = createApp({ config, oidcConfig: oidc.oidcConfig });
  return { app, config, admin, oidc, clientCredentialsCalls };
}

describe('POST /auth/register (ADR-0012 D2)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('cria o utilizador, atribui a role e faz login imediato, sem tokens no corpo/cookies legíveis', async () => {
    const { app, admin, oidc } = await buildRegisterTestApp();
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);

    const accessToken = oidc.fakeAccessToken('user-new-1', ['CUSTOMER'], {
      email: 'nova.conta@example.pt',
      preferredUsername: 'nova.conta@example.pt',
    });
    oidc.setGrantHandler('password', () =>
      tokenSuccessResponse({ access_token: accessToken, refresh_token: 'refresh-new-1', id_token: undefined }),
    );

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'nova.conta@example.pt', password: 'Sup3r$ecreto!', name: 'Nova Conta', role: 'CUSTOMER' });

    expect(res.status).toBe(201);
    expect(res.body).toEqual({
      registered: true,
      session: true,
      user: {
        sub: 'user-new-1',
        email: 'nova.conta@example.pt',
        username: 'nova.conta@example.pt',
        roles: ['CUSTOMER'],
      },
    });

    // O access_token/refresh_token nunca aparecem em cookie nem em corpo.
    const rawSetCookies = (res.headers['set-cookie'] as unknown as string[]).join('\n');
    expect(rawSetCookies).not.toContain(accessToken);
    expect(rawSetCookies).not.toContain('refresh-new-1');
    expect(JSON.stringify(res.body)).not.toContain(accessToken);

    const sessionCookie = pickCookie(res, 'sm_sid');
    expect(sessionCookie).toBeTruthy();

    expect(admin.usersByEmail.has('nova.conta@example.pt')).toBe(true);
    const userId = admin.usersByEmail.get('nova.conta@example.pt')!;
    expect(admin.roleAssignments.get(userId)).toBe('CUSTOMER');
  });

  it('rejeita a role ADMIN (allowlist no servidor, ADR-0012 D2) sem chamar o Keycloak', async () => {
    const { app, admin } = await buildRegisterTestApp();
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);
    const fetchSpy = admin.fetchMock;
    let called = false;
    vi.stubGlobal('fetch', (...args: Parameters<typeof fetch>) => {
      called = true;
      return fetchSpy(...args);
    });

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'aspirante@example.pt', password: 'Sup3r$ecreto!', name: 'Quer Ser Admin', role: 'ADMIN' });

    expect(res.status).toBe(400);
    expect(res.body.type).toBe('https://errors.servimatch.pt/invalid-registration');
    expect(res.body.invalidFields).toContain('role');
    expect(called).toBe(false);
  });

  it('rejeita password fraca com um conjunto fechado de motivos em português, sem chamar o Keycloak', async () => {
    const { app, admin } = await buildRegisterTestApp();
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);
    let called = false;
    const original = admin.fetchMock;
    vi.stubGlobal('fetch', (...args: Parameters<typeof fetch>) => {
      called = true;
      return original(...args);
    });

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'fraca@example.pt', password: '123', name: 'Password Fraca', role: 'CUSTOMER' });

    expect(res.status).toBe(400);
    expect(res.body.type).toBe('https://errors.servimatch.pt/weak-password');
    expect(Array.isArray(res.body.errors)).toBe(true);
    expect(res.body.errors.length).toBeGreaterThan(0);
    expect(called).toBe(false);
  });

  it('devolve 409 quando o email já está registado (D2/D7.3: divergência deliberada do login)', async () => {
    const { app, admin, oidc } = await buildRegisterTestApp();
    admin.usersByEmail.set('ja.existe@example.pt', 'kc-existing-user');

    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);
    oidc.setGrantHandler('password', () => tokenErrorResponse(401, 'invalid_grant'));

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'ja.existe@example.pt', password: 'Sup3r$ecreto!', name: 'Ja Existe', role: 'CUSTOMER' });

    expect(res.status).toBe(409);
    expect(res.body.type).toBe('https://errors.servimatch.pt/email-already-registered');
  });

  it('rollback: apaga o utilizador no Keycloak se a atribuição de role falhar', async () => {
    const { app, admin } = await buildRegisterTestApp();
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);
    admin.controls.forceAssignRoleStatus(500);

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'rollback@example.pt', password: 'Sup3r$ecreto!', name: 'Vai Falhar', role: 'PROVIDER' });

    expect(res.status).toBe(502);
    // Uma conta sem role é pior que inexistente: o utilizador tem de deixar de existir no Keycloak.
    expect(admin.usersByEmail.has('rollback@example.pt')).toBe(false);
    expect(admin.deletedUserIds.length).toBe(1);
  });

  it('regista o órfão (sem PII) quando o rollback também falha, mas ainda devolve erro genérico ao cliente', async () => {
    const { app, admin } = await buildRegisterTestApp();
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);
    admin.controls.forceAssignRoleStatus(500);
    admin.controls.forceDeleteFailure();

    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'orfao@example.pt', password: 'Sup3r$ecreto!', name: 'Vai Ficar Orfao', role: 'PROVIDER' });

    expect(res.status).toBe(502);
    // O utilizador continua no Keycloak (rollback falhou) — órfão, não corrupção.
    expect(admin.usersByEmail.has('orfao@example.pt')).toBe(true);

    const orphanLog = errorSpy.mock.calls.find(
      (call) => typeof call[0] === 'string' && call[0].includes('órfão'),
    );
    expect(orphanLog).toBeTruthy();
    const loggedPayload = JSON.stringify(orphanLog);
    expect(loggedPayload).not.toContain('orfao@example.pt');
    expect(loggedPayload).not.toContain('Vai Ficar Orfao');

    errorSpy.mockRestore();
  });

  it('login automático falhado após registo devolve sucesso do registo sem sessão', async () => {
    const { app, oidc } = await buildRegisterTestApp();
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);
    oidc.setGrantHandler('password', () => tokenErrorResponse(401, 'invalid_grant'));

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'sem.autologin@example.pt', password: 'Sup3r$ecreto!', name: 'Sem Auto Login', role: 'CUSTOMER' });

    expect(res.status).toBe(201);
    expect(res.body).toEqual({ registered: true, session: false });
    expect(pickCookie(res, 'sm_sid')).toBeUndefined();
  });

  it('aplica rate limiting por IP/email antes de chamar o Keycloak', async () => {
    const { app, admin } = await buildRegisterTestApp({
      authRateLimit: { windowMs: 60_000, maxPerIp: 1, maxPerEmail: 5 },
    });
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);

    await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'primeiro@example.pt', password: 'Sup3r$ecreto!', name: 'Primeiro', role: 'CUSTOMER' });

    let called = false;
    const original = admin.fetchMock;
    vi.stubGlobal('fetch', (...args: Parameters<typeof fetch>) => {
      called = true;
      return original(...args);
    });

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'segundo@example.pt', password: 'Sup3r$ecreto!', name: 'Segundo', role: 'CUSTOMER' });

    expect(res.status).toBe(429);
    expect(res.headers['retry-after']).toBeTruthy();
    expect(called).toBe(false);
  });

  it('token do service account: cacheado entre registos, só se pede um novo em 401', async () => {
    const { app, admin, clientCredentialsCalls } = await buildRegisterTestApp({
      authRateLimit: { windowMs: 60_000, maxPerIp: 10, maxPerEmail: 10 },
    });
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);

    await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'cache1@example.pt', password: 'Sup3r$ecreto!', name: 'Cache Um', role: 'CUSTOMER' });
    await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'cache2@example.pt', password: 'Sup3r$ecreto!', name: 'Cache Dois', role: 'CUSTOMER' });

    // Dois registos, UM único token de service account pedido — não um pedido novo por registo.
    expect(clientCredentialsCalls.count).toBe(1);

    // Simula o token a deixar de ser aceite (expirado/revogado no Keycloak):
    // a próxima chamada à Admin REST API devolve 401 uma vez.
    admin.controls.forceNext401();

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'cache3@example.pt', password: 'Sup3r$ecreto!', name: 'Cache Tres', role: 'CUSTOMER' });

    // Reobtém um token novo em resposta ao 401, e repete o pedido com sucesso.
    expect(clientCredentialsCalls.count).toBe(2);
    expect(res.status).toBe(201);
    expect(admin.usersByEmail.has('cache3@example.pt')).toBe(true);
  });
});
