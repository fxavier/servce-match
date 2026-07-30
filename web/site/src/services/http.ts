import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from '../api/generated/schema';

/**
 * Cliente HTTP gerado a partir de `docs/api/openapi.yaml` (`npm run
 * generate:api`). Todas as chamadas ao domínio (`/v1/...`) passam por aqui —
 * nunca por `fetch` à mão. `baseUrl` aponta para o BFF (`VITE_API_BASE`,
 * default `/api`): o site nunca fala diretamente com o backend nem conhece
 * tokens (ADR-0002) — quem fala com o Resource Server é o BFF.
 */
const API_BASE = import.meta.env.VITE_API_BASE ?? '/api';

export const api = createClient<paths>({
  baseUrl: API_BASE,
  credentials: 'include',
  // Indireção deliberada: em vez de deixar o `openapi-fetch` capturar
  // `globalThis.fetch` uma única vez na criação do cliente (módulo
  // avaliado uma só vez, por cache de módulos ES), procura-se `fetch` em
  // `globalThis` a cada pedido. Sem isto, testes que troquem
  // `globalThis.fetch` depois deste módulo já ter sido importado (`vi.stubGlobal`)
  // continuariam presos à implementação real — sintoma: `ECONNREFUSED`
  // contra `localhost:80` em vez do mock. Não muda nada em runtime real,
  // onde `globalThis.fetch` nunca muda depois do arranque.
  fetch: (...args: Parameters<typeof fetch>) => globalThis.fetch(...args),
});

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/**
 * Proteção CSRF do BFF (padrão double-submit — ver web/bff/src/csrf.ts):
 * repete em `X-CSRF-Token` o valor do cookie `sm_csrf`, não-HttpOnly de
 * propósito, em todo o pedido que muda estado.
 */
const csrfMiddleware: Middleware = {
  onRequest({ request }) {
    if (!SAFE_METHODS.has(request.method)) {
      const token = readCookie('sm_csrf');
      if (token) request.headers.set('X-CSRF-Token', token);
    }
    return request;
  },
};

api.use(csrfMiddleware);
