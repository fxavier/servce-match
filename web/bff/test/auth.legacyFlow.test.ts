import { afterEach, describe, expect, it, vi } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { testConfig } from './testConfig.js';
import { createTestOidcSetup } from './testOidc.js';

describe('Fluxo de regresso Authorization Code + PKCE — desativado por omissão (ADR-0012 D1/D10)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('GET /auth/login e GET /auth/callback não existem (404) quando a flag está desligada', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const loginRes = await request(app).get('/auth/login?returnTo=/x');
    expect(loginRes.status).toBe(404);

    const callbackRes = await request(app).get('/auth/callback?code=abc&state=xyz');
    expect(callbackRes.status).toBe(404);
  });

  it('não é reativável por cabeçalho nem por parâmetro de pedido — só por configuração do servidor', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const attempts = [
      request(app).get('/auth/login').set('X-Legacy-Oidc-Flow', 'true'),
      request(app).get('/auth/login?legacyOidcFlow=true'),
      request(app).get('/auth/login?enabled=1'),
      request(app).get('/auth/login').set('X-Enable-Legacy-Flow', '1'),
    ];
    for (const attempt of attempts) {
      const res = await attempt;
      expect(res.status).toBe(404);
    }
  });

  it('POST /auth/login novo continua a funcionar mesmo com a flag desligada — não há conflito de rota entre GET e POST', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false } });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const csrfRes = await request(app).get('/auth/me');
    const raw = csrfRes.headers['set-cookie'] as unknown as string[];
    const csrfCookie = raw.find((c) => c.startsWith('sm_csrf='))!;
    const csrfToken = csrfCookie.split('=')[1].split(';')[0];

    const res = await request(app)
      .post('/auth/login')
      .set('Cookie', [csrfCookie])
      .set('X-CSRF-Token', csrfToken)
      .send({ email: '', password: '' });

    // Não é 404 — a rota POST existe e responde com validação normal.
    expect(res.status).toBe(400);
  });

  it('quando ligada por configuração, GET /auth/login volta a funcionar (caminho de regresso genuíno)', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: true } });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const res = await request(app).get('/auth/login?returnTo=/dashboard');
    expect(res.status).toBe(302);
    expect(res.headers.location).toContain('/protocol/openid-connect/auth');
  });
});
