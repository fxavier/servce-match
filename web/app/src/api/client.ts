import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './generated/schema';

/**
 * Cliente HTTP gerado a partir de docs/api/openapi.yaml (ver
 * `npm run generate:api`). Todas as chamadas ao domínio (`/v1/...`) passam
 * por aqui — nunca por `fetch` à mão. `baseUrl: '/api'` porque quem fala com
 * o backend é o BFF: a SPA nunca conhece o URL nem os tokens do backend
 * (ADR-0002).
 */
// URL absoluta (mesma origem) em vez de relativa: alguns interceptores de
// `Request` usados em testes (MSW/@mswjs/interceptors) não resolvem URLs
// relativas sem uma base explícita. Em produção continua a ser a mesma
// origem da SPA — o proxy do BFF (Vite em dev; reverse proxy atrás dele em
// produção) intercepta pelo caminho, não pela forma como o URL foi escrito.
export const api = createClient<paths>({
  baseUrl: `${window.location.origin}/api`,
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
