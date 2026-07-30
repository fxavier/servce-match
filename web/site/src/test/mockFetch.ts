/** Helpers partilhados pelos testes que mockam `global.fetch` (camada HTTP real, sem MSW). */

/** Extrai o URL pedido de qualquer forma de `RequestInfo` — nunca `String(request)` (dá "[object Request]"). */
export function urlOf(input: RequestInfo | URL): string {
  if (typeof input === 'string') return input;
  if (input instanceof URL) return input.toString();
  return input.url;
}

/**
 * Extrai o método HTTP. O `openapi-fetch` chama `fetch(request, extraInit)`
 * com um `Request` já construído (que já carrega o método) — `extraInit`
 * não repete `method`, por isso ler só de `init?.method` dá sempre `'GET'`
 * por omissão e mascara silenciosamente qualquer `POST`/`PUT`.
 */
export function methodOf(input: RequestInfo | URL, init?: RequestInit): string {
  if (init?.method) return init.method;
  if (typeof Request !== 'undefined' && input instanceof Request) return input.method;
  return 'GET';
}

export function jsonResponse(body: unknown, init?: ResponseInit): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
    ...init,
  });
}

export function problemResponse(status: number, type: string, title: string): Response {
  return new Response(JSON.stringify({ type, title, status }), {
    status,
    headers: { 'content-type': 'application/problem+json' },
  });
}
