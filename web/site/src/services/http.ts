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
