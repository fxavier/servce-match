import { afterEach, describe, expect, it, vi } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { testConfig } from './testConfig.js';
import { createTestOidcSetup } from './testOidc.js';

/**
 * M4 (auditoria Onda C): sem reencaminhar `X-Forwarded-For`, o backend só
 * vê o IP do BFF em `getRemoteAddr()` para TODO o tráfego da SPA — o balde
 * de `servimatch.rate-limit.capacity` passa a ser partilhado por toda a
 * base de utilizadores web (auto-DoS). A correção tem de reencaminhar o IP
 * que o EXPRESS calculou (`req.ip`, que já respeita `trust proxy`), nunca
 * o valor bruto que o cliente enviou — copiar o cabeçalho do cliente
 * reabriria exatamente o defeito que o backend corrigiu nesta sessão
 * (atacante controla a chave do balde com um `X-Forwarded-For` forjado).
 */
describe('proxy /api/** — X-Forwarded-For reencaminhado ao backend (M4, auditoria Onda C)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sem trust proxy configurado (0 saltos): o cabeçalho X-Forwarded-For forjado pelo cliente é ignorado — o backend recebe o IP real da ligação, nunca o valor forjado', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false }, trustProxyHops: 0 });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const backendFetch = vi
      .fn()
      .mockResolvedValue(new Response('[]', { status: 200, headers: { 'content-type': 'application/json' } }));
    vi.stubGlobal('fetch', backendFetch);

    const forgedIp = '203.0.113.99';
    const res = await request(app).get('/api/v1/categories').set('X-Forwarded-For', forgedIp);
    expect(res.status).toBe(200);

    const forwardedHeaders = backendFetch.mock.calls[0][1].headers as Headers;
    const forwarded = forwardedHeaders.get('x-forwarded-for');
    // Sem `trust proxy` configurado, o Express ignora por completo o
    // cabeçalho recebido — `req.ip` é sempre o IP do socket TCP (loopback,
    // neste teste). O valor forjado pelo cliente NUNCA chega ao backend.
    expect(forwarded).toBeTruthy();
    expect(forwarded).not.toBe(forgedIp);
  });

  it('com trust proxy = 1 salto explícito: reencaminha o IP CALCULADO pelo Express (req.ip), não a string em bruto do cabeçalho do cliente', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false }, trustProxyHops: 1 });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const backendFetch = vi
      .fn()
      .mockResolvedValue(new Response('[]', { status: 200, headers: { 'content-type': 'application/json' } }));
    vi.stubGlobal('fetch', backendFetch);

    // Um atacante tenta prefixar uma entrada extra na cadeia — se o BFF
    // alguma vez reencaminhasse o cabeçalho tal como chegou (ou o
    // concatenasse), o backend veria "9.9.9.9" como a entrada mais à
    // esquerda, controlável pelo atacante. Com `trust proxy = 1`, o Express
    // só confia num salto de proxy — o IP real do requerente é a entrada
    // mais próxima do BFF (a última), não a que o atacante controla.
    const res = await request(app)
      .get('/api/v1/categories')
      .set('X-Forwarded-For', '9.9.9.9, 8.8.8.8');
    expect(res.status).toBe(200);

    const forwardedHeaders = backendFetch.mock.calls[0][1].headers as Headers;
    const forwarded = forwardedHeaders.get('x-forwarded-for');

    // O valor reencaminhado é exatamente o que o Express calculou para
    // `req.ip` (8.8.8.8, o salto mais próximo do BFF) — nunca a string
    // bruta do cabeçalho ("9.9.9.9, 8.8.8.8") nem a entrada controlada
    // pelo atacante ("9.9.9.9").
    expect(forwarded).toBe('8.8.8.8');
    expect(forwarded).not.toBe('9.9.9.9, 8.8.8.8');
    expect(forwarded).not.toBe('9.9.9.9');
  });

  it('X-Forwarded-For nunca fica ausente para um pedido normal (visitante direto, sem proxy) — o backend consegue sempre aplicar rate limiting por IP', async () => {
    const oidc = await createTestOidcSetup();
    const config = testConfig({ legacyOidcFlow: { enabled: false }, trustProxyHops: 0 });
    const app = createApp({ config, oidcConfig: oidc.oidcConfig });

    const backendFetch = vi
      .fn()
      .mockResolvedValue(new Response('[]', { status: 200, headers: { 'content-type': 'application/json' } }));
    vi.stubGlobal('fetch', backendFetch);

    const res = await request(app).get('/api/v1/categories');
    expect(res.status).toBe(200);

    const forwardedHeaders = backendFetch.mock.calls[0][1].headers as Headers;
    expect(forwardedHeaders.get('x-forwarded-for')).toBeTruthy();
  });
});
