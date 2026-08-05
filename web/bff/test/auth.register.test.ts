import { afterEach, describe, expect, it, vi } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { testConfig } from './testConfig.js';
import { createTestOidcSetup, tokenSuccessResponse } from './testOidc.js';
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

describe('POST /auth/register (ADR-0012 D2, D7.3; M2 — sem Set-Cookie em nenhum ramo)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('cria o utilizador e atribui a role, mas NUNCA estabelece sessão do browser (sem Set-Cookie, sem tokens no corpo)', async () => {
    const { app, admin } = await buildRegisterTestApp();
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);

    const res = await request(app)
      .post('/auth/register')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'nova.conta@example.pt', password: 'Sup3r$ecreto!', name: 'Nova Conta', role: 'CUSTOMER' });

    expect(res.status).toBe(201);
    // O corpo é LITERALMENTE `{ registered: true }` — nem `session` nem
    // `user`. Quem quer saber se está autenticado chama `GET /auth/me`
    // depois de um `POST /auth/login` separado, como o site já faz.
    expect(res.body).toEqual({ registered: true });

    // M2 (auditoria Onda C): `/auth/register` nunca define `sm_sid`, nem no
    // ramo de email novo. Um `Set-Cookie` presente só quando a conta é nova
    // era um oráculo direto para um cliente HTTP de primeira parte (não
    // sujeito à regra "forbidden response header", que só protege contra
    // leitura CROSS-SITE dentro do browser da vítima).
    expect(res.headers['set-cookie']).toBeUndefined();
    expect(pickCookie(res, 'sm_sid')).toBeUndefined();

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

  it('email já registado: NUNCA 409 — mesma resposta (201, mesmo corpo, sem Set-Cookie) que um registo novo, sem vestígio do conflito (fecha C3.1)', async () => {
    const { app, admin } = await buildRegisterTestApp();
    admin.usersByEmail.set('ja.existe@example.pt', 'kc-existing-user');

    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);

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
    // Nenhuma sessão em nenhum ramo — nem o requerente sem sessão nem o
    // titular real (nunca se autentica dentro de `/auth/register`).
    expect(res.headers['set-cookie']).toBeUndefined();
    expect(pickCookie(res, 'sm_sid')).toBeUndefined();
  });

  it('email novo e email já registado produzem resposta indistinguível — status, corpo E CABEÇALHOS, Set-Cookie incluído (M2, auditoria Onda C)', async () => {
    const { app: appNew } = await buildRegisterTestApp();
    const csrfNew = await getCsrf(appNew);
    const resNew = await request(appNew)
      .post('/auth/register')
      .set('Cookie', [csrfNew.cookie])
      .set('X-CSRF-Token', csrfNew.token)
      .send({ email: 'comparacao.nova@example.pt', password: 'Sup3r$ecreto!', name: 'Comparacao Nova', role: 'CUSTOMER' });

    const { app: appExisting, admin: adminExisting } = await buildRegisterTestApp();
    adminExisting.usersByEmail.set('comparacao.existente@example.pt', 'kc-cmp-existing');
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

    // Mesmo corpo LITERALMENTE.
    expect(resNew.body).toEqual({ registered: true });
    expect(resExisting.body).toEqual(resNew.body);

    // A asserção que fecha M2: nem um `Set-Cookie` a mais, em NENHUM dos
    // dois casos — não "um tem, outro não" (essa era a assimetria C3.1 do
    // cabeçalho), e não "os dois têm um cookie anónimo" (a alternativa mais
    // fraca, descartada: complicaria a semântica de sessão sem necessidade,
    // dado que a SPA já encadeia `POST /auth/login` a seguir). Confirma a
    // DIFERENÇA — ou melhor, a ausência dela — nunca um mínimo que passaria
    // sempre.
    expect(resNew.headers['set-cookie']).toBeUndefined();
    expect(resExisting.headers['set-cookie']).toBeUndefined();

    // O estado real do servidor diverge (só o email novo criou conta) — a
    // indistinguibilidade é da resposta HTTP, não do estado.
    expect(adminExisting.usersByEmail.has('comparacao.nova@example.pt')).toBe(false);

    // Cabeçalhos de resposta relevantes para o cliente também não variam
    // com o ramo interno.
    expect(resExisting.headers['content-type']).toBe(resNew.headers['content-type']);
  });

  it('tempo de resposta indistinguível entre email novo e email já registado, mesmo com trabalho real assimétrico (criar+atribuir role vs. nenhuma chamada ao Keycloak)', async () => {
    const timing = { floorMs: 150, quantumMs: 80, maxDelayMs: 1_000 };

    // --- Caminho de email NOVO -------------------------------------------
    // Cria + atribui role: duas chamadas reais à Admin REST API.
    const { app: appNew } = await buildRegisterTestApp({ registerTiming: timing });
    const csrfNew = await getCsrf(appNew);

    const startNew = Date.now();
    const resNew = await request(appNew)
      .post('/auth/register')
      .set('Cookie', [csrfNew.cookie])
      .set('X-CSRF-Token', csrfNew.token)
      .send({ email: 'tempo.novo@example.pt', password: 'Sup3r$ecreto!', name: 'Tempo Novo', role: 'CUSTOMER' });
    const elapsedNew = Date.now() - startNew;

    // --- Caminho de email JÁ REGISTADO ------------------------------------
    // Bem menos trabalho: uma única chamada `createUser` que devolve
    // conflito, e nenhuma chamada ao Keycloak depois disso (nunca se tenta
    // autenticar). Sem `withNormalizedTiming` a cobrir os dois ramos por
    // igual, este caminho seria SEMPRE mais rápido, de forma mensurável.
    const { app: appExisting, admin: adminExisting } = await buildRegisterTestApp({ registerTiming: timing });
    adminExisting.usersByEmail.set('tempo.existente@example.pt', 'kc-tempo-existing');
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

    // Os dois tempos reais são naturalmente diferentes (o caminho novo faz
    // duas chamadas reais ao IdP, o outro nenhuma) mas ambos caem na MESMA
    // janela quantizada — o piso, dimensionado para o caminho mais pesado.
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

  it('email já registado consome a quota do LOGIN para o mesmo (ip,email) — /auth/register não é um canal extra de força bruta', async () => {
    const { app, admin } = await buildRegisterTestApp({
      authRateLimit: { windowMs: 60_000, maxPerIp: 20, maxPerEmail: 5 },
    });
    admin.usersByEmail.set('vitima@example.pt', 'kc-vitima');
    const { cookie: csrfCookie, token: csrfToken } = await getCsrf(app);

    // 5 tentativas de registo contra o mesmo email já existente esgotam a
    // quota de LOGIN desse (ip,email) — não só a quota (própria) do registo.
    for (let i = 0; i < 5; i += 1) {
      const res = await request(app)
        .post('/auth/register')
        .set('Cookie', [csrfCookie])
        .set('X-CSRF-Token', csrfToken)
        .send({ email: 'vitima@example.pt', password: `Tentativa$${i}!`, name: 'Atacante', role: 'CUSTOMER' });
      expect(res.status).toBe(201);
    }

    // Uma tentativa a seguir de LOGIN genuíno contra a mesma vítima já
    // encontra a quota esgotada — 429, nunca chega a contactar o Keycloak.
    const loginRes = await request(app)
      .post('/auth/login')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: 'vitima@example.pt', password: 'PasswordReal!' });

    expect(loginRes.status).toBe(429);
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
