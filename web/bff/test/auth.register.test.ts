import { afterEach, describe, expect, it, vi } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { testConfig } from './testConfig.js';
import { createTestOidcSetup, delay, tokenErrorResponse, tokenSuccessResponse } from './testOidc.js';
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

describe('POST /auth/register (ADR-0012 D2, D7.3)', () => {
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
    // O corpo é LITERALMENTE `{ registered: true }` — nem `session` nem
    // `user`, para nunca variar com o resultado interno do registo (ver
    // o teste de indistinguibilidade, abaixo, e o comentário em
    // `src/routes/auth.ts` junto à construção desta resposta). Quem quer
    // saber se ficou autenticado chama `GET /auth/me`, como o site já faz.
    expect(res.body).toEqual({ registered: true });

    // O access_token/refresh_token nunca aparecem em cookie nem em corpo.
    const rawSetCookies = (res.headers['set-cookie'] as unknown as string[]).join('\n');
    expect(rawSetCookies).not.toContain(accessToken);
    expect(rawSetCookies).not.toContain('refresh-new-1');
    expect(JSON.stringify(res.body)).not.toContain(accessToken);

    // A sessão foi mesmo criada (cookie presente) — só não é anunciada no
    // corpo da resposta.
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

  it('email já registado: NUNCA 409 — mesma resposta (201, mesmo corpo) que um registo novo, sem sessão nem vestígio do conflito (fecha C3.1)', async () => {
    const { app, admin, oidc } = await buildRegisterTestApp();
    admin.usersByEmail.set('ja.existe@example.pt', 'kc-existing-user');

    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);
    // O requerente não é o titular da conta: não conhece a password real,
    // por isso a tentativa de login com a password submetida falha — como
    // aconteceria com qualquer password errada em `/auth/login`.
    oidc.setGrantHandler('password', () => tokenErrorResponse(401, 'invalid_grant'));

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'ja.existe@example.pt', password: 'Sup3r$ecreto!', name: 'Ja Existe', role: 'CUSTOMER' });

    // 409 seria exatamente o oráculo de enumeração que esta tarefa fecha —
    // a divergência anterior citava ADR-0012 D7.3 como autorização, mas
    // D7.3 diz o contrário: "no registo, mesma resposta para email novo e
    // para email já registado — caso contrário o oráculo apenas mudou de
    // porta" (docs/adr/0012-*.md).
    expect(res.status).toBe(201);
    expect(res.body).toEqual({ registered: true });
    // Nem `email-already-registered`, nem `409`, nem qualquer outro vestígio
    // de que a conta já existia chega ao corpo da resposta.
    expect(JSON.stringify(res.body)).not.toMatch(/409|already|existe/i);
    // Nenhuma sessão para quem não provou conhecer a password do titular.
    expect(pickCookie(res, 'sm_sid')).toBeUndefined();
  });

  it('email novo e email já registado produzem EXATAMENTE a mesma resposta (mesmo status, mesmo corpo) — tal como o login já faz para conta inexistente vs password errada (ADR-0012 D7.3)', async () => {
    const { app: appNew, oidc: oidcNew } = await buildRegisterTestApp();
    oidcNew.setGrantHandler('password', () =>
      tokenSuccessResponse({
        access_token: oidcNew.fakeAccessToken('user-cmp-new', ['CUSTOMER']),
        refresh_token: 'refresh-cmp-new',
      }),
    );
    const csrfNew = await getCsrf(appNew);
    const resNew = await request(appNew)
      .post('/auth/register')
      .set('Cookie', [csrfNew.cookie])
      .set('X-CSRF-Token', csrfNew.token)
      .send({ email: 'comparacao.nova@example.pt', password: 'Sup3r$ecreto!', name: 'Comparacao Nova', role: 'CUSTOMER' });

    const { app: appExisting, admin: adminExisting, oidc: oidcExisting } = await buildRegisterTestApp();
    adminExisting.usersByEmail.set('comparacao.existente@example.pt', 'kc-cmp-existing');
    oidcExisting.setGrantHandler('password', () => tokenErrorResponse(401, 'invalid_grant'));
    const csrfExisting = await getCsrf(appExisting);
    const resExisting = await request(appExisting)
      .post('/auth/register')
      .set('Cookie', [csrfExisting.cookie])
      .set('X-CSRF-Token', csrfExisting.token)
      .send({
        email: 'comparacao.existente@example.pt',
        password: 'Sup3r$ecreto!',
        name: 'Comparacao Existente',
        role: 'CUSTOMER',
      });

    expect(resNew.status).toBe(201);
    expect(resExisting.status).toBe(resNew.status);

    // Mesmo corpo LITERALMENTE — nem `session` nem `user` variam com o
    // resultado interno: se variassem (`session: true` só quando o email é
    // mesmo novo), o oráculo apenas teria mudado de campo do corpo em vez
    // de status code.
    expect(resNew.body).toEqual({ registered: true });
    expect(resExisting.body).toEqual(resNew.body);

    // Confirma que os dois casos tiveram mesmo destinos INTERNOS diferentes
    // (uma sessão foi mesmo criada só no caso novo) — a indistinguibilidade
    // é da resposta HTTP, não do estado real do servidor.
    expect(pickCookie(resNew, 'sm_sid')).toBeTruthy();
    expect(pickCookie(resExisting, 'sm_sid')).toBeUndefined();
  });

  it('tempo de resposta indistinguível entre email novo e email já registado, mesmo com trabalho real assimétrico — o caminho de conflito passa por withNormalizedTiming tal como o de sucesso', async () => {
    const timing = { floorMs: 150, quantumMs: 80, maxDelayMs: 1_000 };

    // --- Caminho de email NOVO -------------------------------------------
    // Cria + atribui role (chamadas à Admin REST API, instantâneas no
    // duplo) e só depois autentica com a password que acabou de definir —
    // aqui com um atraso real de 20ms, a representar trabalho genuíno do
    // IdP (derivação da nova credencial).
    const oidcNew = await createTestOidcSetup();
    const configNew = testConfig({ legacyOidcFlow: { enabled: false }, registerTiming: timing });
    const adminNew = createKeycloakAdminMock(configNew.keycloak.adminApiBaseUrl);
    vi.stubGlobal('fetch', adminNew.fetchMock);
    oidcNew.setGrantHandler('client_credentials', () =>
      tokenSuccessResponse({ access_token: 'admin-service-account-token-timing-new' }),
    );
    oidcNew.setGrantHandler('password', async () => {
      await delay(20);
      return tokenSuccessResponse({
        access_token: oidcNew.fakeAccessToken('user-timing-new', ['CUSTOMER']),
        refresh_token: 'refresh-timing-new',
      });
    });
    const appNew = createApp({ config: configNew, oidcConfig: oidcNew.oidcConfig });
    const csrfNew = await getCsrf(appNew);

    const startNew = Date.now();
    const resNew = await request(appNew)
      .post('/auth/register')
      .set('Cookie', [csrfNew.cookie])
      .set('X-CSRF-Token', csrfNew.token)
      .send({ email: 'tempo.novo@example.pt', password: 'Sup3r$ecreto!', name: 'Tempo Novo', role: 'CUSTOMER' });
    const elapsedNew = Date.now() - startNew;

    // --- Caminho de email JÁ REGISTADO ------------------------------------
    // Bem menos trabalho (nem cria, nem atribui role) — só uma tentativa de
    // login com a password submetida, que aqui falha depressa mas com um
    // atraso REAL deliberadamente MAIOR (55ms) do que o caminho de sucesso,
    // para provar que a normalização não depende de qual dos dois é
    // "naturalmente" mais lento. Um teste que só dormisse "um bocado" nos
    // dois casos não provaria nada — a assimetria tem de ser real.
    const oidcExisting = await createTestOidcSetup();
    const configExisting = testConfig({ legacyOidcFlow: { enabled: false }, registerTiming: timing });
    const adminExisting = createKeycloakAdminMock(configExisting.keycloak.adminApiBaseUrl);
    adminExisting.usersByEmail.set('tempo.existente@example.pt', 'kc-tempo-existing');
    vi.stubGlobal('fetch', adminExisting.fetchMock);
    oidcExisting.setGrantHandler('client_credentials', () =>
      tokenSuccessResponse({ access_token: 'admin-service-account-token-timing-existing' }),
    );
    oidcExisting.setGrantHandler('password', async () => {
      await delay(55);
      return tokenErrorResponse(401, 'invalid_grant');
    });
    const appExisting = createApp({ config: configExisting, oidcConfig: oidcExisting.oidcConfig });
    const csrfExisting = await getCsrf(appExisting);

    const startExisting = Date.now();
    const resExisting = await request(appExisting)
      .post('/auth/register')
      .set('Cookie', [csrfExisting.cookie])
      .set('X-CSRF-Token', csrfExisting.token)
      .send({
        email: 'tempo.existente@example.pt',
        password: 'Sup3r$ecreto!',
        name: 'Tempo Existente',
        role: 'CUSTOMER',
      });
    const elapsedExisting = Date.now() - startExisting;

    expect(resNew.status).toBe(201);
    expect(resExisting.status).toBe(201);
    expect(resNew.body).toEqual({ registered: true });
    expect(resExisting.body).toEqual({ registered: true });

    // As duas latências reais simuladas (20ms e 55ms) são diferentes e
    // ambas abaixo do piso (150ms) — sem `withNormalizedTiming` a cobrir
    // TAMBÉM o caminho de conflito (a regressão original: o 409 devolvia-se
    // de imediato, sem esperar pelo piso), os dois tempos de resposta
    // seriam mensuravelmente diferentes. Com a normalização a cobrir os
    // dois, ambos caem na MESMA janela quantizada — o piso.
    expect(elapsedNew).toBeGreaterThanOrEqual(timing.floorMs);
    expect(elapsedExisting).toBeGreaterThanOrEqual(timing.floorMs);
    expect(elapsedNew).toBeLessThan(timing.floorMs + timing.quantumMs);
    expect(elapsedExisting).toBeLessThan(timing.floorMs + timing.quantumMs);
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

  it('login automático falhado após registo devolve sucesso do registo sem sessão, com o MESMO corpo do caminho de conflito', async () => {
    const { app, oidc } = await buildRegisterTestApp();
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);
    oidc.setGrantHandler('password', () => tokenErrorResponse(401, 'invalid_grant'));

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'sem.autologin@example.pt', password: 'Sup3r$ecreto!', name: 'Sem Auto Login', role: 'CUSTOMER' });

    expect(res.status).toBe(201);
    expect(res.body).toEqual({ registered: true });
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
