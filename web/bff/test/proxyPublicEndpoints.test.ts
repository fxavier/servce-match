import { afterEach, describe, expect, it, vi } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { SessionStore } from '../src/session.js';
import { testConfig } from './testConfig.js';
import { createTestOidcSetup, tokenErrorResponse } from './testOidc.js';
import { getCsrf } from './testHttp.js';

/**
 * Correção do defeito: a homepage pública (categorias, pesquisa, perfil de
 * prestador) partia para visitantes anónimos porque `requireSession` se
 * aplicava a TODO o `/api/**`, incluindo endpoints que o contrato declara
 * `security: []`. Ver docs/prompts/bff-endpoints-publicos.txt.
 */
describe('proxy /api/** — endpoints públicos do contrato (sessão opcional, nunca ignorada)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('GET /api/v1/categories sem cookie → 200, não 401', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const backendFetch = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify([{ id: 'cat-1', name: 'Canalização' }]), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
      );
    vi.stubGlobal('fetch', backendFetch);

    const res = await request(app).get('/api/v1/categories');
    expect(res.status).toBe(200);
    expect(res.body).toEqual([{ id: 'cat-1', name: 'Canalização' }]);

    // Visitante anónimo: nenhum Authorization inventado.
    const forwardedHeaders = backendFetch.mock.calls[0][1].headers as Headers;
    expect(forwardedHeaders.has('authorization')).toBe(false);
  });

  it('GET /api/v1/categories com sessão válida → 200 e o token vai no Authorization (sessão opcional ≠ sessão ignorada)', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const sessions = new SessionStore(config.session.absoluteTtlSeconds);
    const app = createApp({ config, oidcConfig: oidc.oidcConfig, sessions });

    const accessToken = oidc.fakeAccessToken('user-authenticated', ['CUSTOMER']);
    const session = sessions.create({ accessToken, refreshToken: 'refresh-1', expiresInSeconds: 300 });

    const backendFetch = vi
      .fn()
      .mockResolvedValue(new Response('[]', { status: 200, headers: { 'content-type': 'application/json' } }));
    vi.stubGlobal('fetch', backendFetch);

    const res = await request(app).get('/api/v1/categories').set('Cookie', [`sm_sid=${session.id}`]);
    expect(res.status).toBe(200);

    const forwardedHeaders = backendFetch.mock.calls[0][1].headers as Headers;
    expect(forwardedHeaders.get('authorization')).toBe(`Bearer ${accessToken}`);
  });

  it('GET /api/v1/categories com sessão expirada e refresh falhado → 200 como anónimo, não 401', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const sessions = new SessionStore(config.session.absoluteTtlSeconds);
    const app = createApp({ config, oidcConfig: oidc.oidcConfig, sessions });

    const accessToken = oidc.fakeAccessToken('user-stale', ['CUSTOMER']);
    const session = sessions.create({ accessToken, refreshToken: 'refresh-dead', expiresInSeconds: 1 });
    oidc.setGrantHandler('refresh_token', () => tokenErrorResponse(400, 'invalid_grant'));

    const backendFetch = vi
      .fn()
      .mockResolvedValue(new Response('[]', { status: 200, headers: { 'content-type': 'application/json' } }));
    vi.stubGlobal('fetch', backendFetch);

    const res = await request(app).get('/api/v1/categories').set('Cookie', [`sm_sid=${session.id}`]);

    // Um cookie velho não pode partir a homepage: segue como visitante anónimo.
    expect(res.status).toBe(200);
    const forwardedHeaders = backendFetch.mock.calls[0][1].headers as Headers;
    expect(forwardedHeaders.has('authorization')).toBe(false);
    // A sessão inválida foi mesmo descartada (não fica presa a tentar-se para sempre).
    expect(sessions.get(session.id)).toBeUndefined();
  });

  it('GET /api/v1/requests sem cookie → continua 401 Problem Details (endpoint protegido)', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const res = await request(app).get('/api/v1/requests');
    expect(res.status).toBe(401);
    expect(res.body.type).toBe('https://errors.servimatch.pt/unauthenticated');
  });

  it('PUT /api/v1/providers/me sem cookie → continua 401 — apanha a armadilha do prefixo `/v1/providers/*`', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const { cookie, token } = await getCsrf(app);
    const res = await request(app)
      .put('/api/v1/providers/me')
      .set('Cookie', [cookie])
      .set('X-CSRF-Token', token)
      .send({ businessName: 'Nova Empresa' });

    expect(res.status).toBe(401);
    expect(res.body.type).toBe('https://errors.servimatch.pt/unauthenticated');
  });

  it('GET /api/v1/providers/me sem cookie → continua 401 (literal "me", não instância de {providerId})', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const res = await request(app).get('/api/v1/providers/me');
    expect(res.status).toBe(401);
  });

  it('GET /api/v1/providers/prov-123 sem cookie → 200 (instância real de {providerId}, pública)', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const backendFetch = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ id: 'prov-123' }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
      );
    vi.stubGlobal('fetch', backendFetch);

    const res = await request(app).get('/api/v1/providers/prov-123');
    expect(res.status).toBe(200);
  });

  it('POST num caminho público → não é público: continua a exigir CSRF e sessão', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    // Sem token CSRF: rejeitado antes de sequer chegar à lógica de sessão.
    const noCsrfRes = await request(app).post('/api/v1/categories').send({});
    expect(noCsrfRes.status).toBe(403);
    expect(noCsrfRes.body.type).toBe('https://errors.servimatch.pt/csrf-rejected');

    // Com CSRF válido mas sem sessão: 401 — POST /v1/categories nunca esteve
    // na allowlist (só o GET tem `security: []` no contrato).
    const { cookie, token } = await getCsrf(app);
    const withCsrfRes = await request(app)
      .post('/api/v1/categories')
      .set('Cookie', [cookie])
      .set('X-CSRF-Token', token)
      .send({});
    expect(withCsrfRes.status).toBe(401);
    expect(withCsrfRes.body.type).toBe('https://errors.servimatch.pt/unauthenticated');
  });
});
