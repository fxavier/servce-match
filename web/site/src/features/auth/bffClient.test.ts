import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, problemResponse } from '../../test/mockFetch';
import * as bffClient from './bffClient';

/**
 * `POST /auth/login`/`/auth/register`/`/auth/logout` (ADR-0012) — os únicos
 * `fetch` escritos à mão do site. Nunca há redirect para o Keycloak, nunca
 * um token no corpo da resposta, e o cabeçalho CSRF acompanha sempre o
 * `POST` (double-submit, `web/bff/src/csrf.ts`).
 */
describe('bffClient', () => {
  afterEach(() => {
    document.cookie = 'sm_csrf=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
    vi.unstubAllGlobals();
  });

  it('fetchSession devolve undefined quando não autenticado', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => jsonResponse({ authenticated: false })),
    );
    await expect(bffClient.fetchSession()).resolves.toBeUndefined();
  });

  it('fetchSession mapeia a sessão autenticada, filtrando roles desconhecidas', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        jsonResponse({
          authenticated: true,
          user: {
            sub: 'c-0001',
            email: 'customer.test@servimatch.pt',
            username: 'customer.test',
            roles: ['CUSTOMER', 'SOMETHING_ELSE'],
          },
        }),
      ),
    );
    await expect(bffClient.fetchSession()).resolves.toEqual({
      id: 'c-0001',
      displayName: 'customer.test',
      email: 'customer.test@servimatch.pt',
      roles: ['CUSTOMER'],
    });
  });

  it('login envia o cabeçalho CSRF a repetir o cookie sm_csrf', async () => {
    document.cookie = 'sm_csrf=test-csrf-token';
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init: RequestInit) => new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await bffClient.login({ email: 'customer.test@servimatch.pt', password: 'hunter2' });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/auth/login');
    expect(init.method).toBe('POST');
    expect(init.credentials).toBe('include');
    expect((init.headers as Record<string, string>)['X-CSRF-Token']).toBe('test-csrf-token');
  });

  it('login lança o ProblemDetails devolvido pelo servidor em credenciais inválidas, sem reinterpretar a mensagem', async () => {
    const problem = {
      type: 'https://errors.servimatch.pt/invalid-credentials',
      title: 'Não foi possível autenticar.',
      status: 401,
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(() => problemResponse(problem.status, problem.type, problem.title)),
    );

    await expect(bffClient.login({ email: 'x@example.com', password: 'wrong' })).rejects.toEqual(problem);
  });

  it('register não lança quando o servidor responde com sucesso — a autenticação real confirma-se só via fetchSession', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Response(null, { status: 204 })),
    );
    await expect(
      bffClient.register({ name: 'Mariana Costa', email: 'mariana@example.com', password: 'hunter22', role: 'CUSTOMER' }),
    ).resolves.toBeUndefined();
  });

  it('logout faz POST para /auth/logout sem esperar logoutUrl no corpo', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init: RequestInit) => new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await bffClient.logout();

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/auth/logout');
    expect(init.method).toBe('POST');
  });
});
