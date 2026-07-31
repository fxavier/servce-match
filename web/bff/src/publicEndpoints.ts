/**
 * Endpoints do contrato que o backend serve sem autenticação (`security: []`
 * em `docs/api/openapi.yaml`, espelhado em `SecurityConfig.PUBLIC_GET_ENDPOINTS`
 * no backend). Isto é uma allowlist por MÉTODO + CAMINHO, nunca por prefixo:
 * `GET /v1/providers/{providerId}` ser público não torna `PUT /v1/providers/me`
 * público, e um `path.startsWith('/v1/providers')` cometeria exatamente esse
 * erro — é por isso que `/v1/providers/me` está explicitamente excluído
 * abaixo, apesar de "encaixar" na forma `/v1/providers/{algo}`.
 *
 * Fica escrita à mão em vez de derivada do YAML do contrato em runtime — não
 * vale a pena trazer um parser de YAML como dependência de produção só para
 * isto. A derivação existe à mesma, mas em tempo de teste:
 * `test/publicEndpoints.contract.test.ts` lê `docs/api/openapi.yaml` e falha
 * se esta lista e o contrato divergirem, sem o BFF depender disso em
 * runtime.
 *
 * Se alterares esta lista, `SecurityConfig.PUBLIC_GET_ENDPOINTS` (backend) e
 * `security: []` (contrato) têm de mudar também — os três têm de concordar.
 *
 * Exclusão deliberada: `POST /v1/webhooks/payments/{gateway}` também tem
 * `security: []` no contrato, mas não é "público" no sentido de "qualquer
 * visitante o pode chamar" — é autenticado pela assinatura do gateway de
 * pagamento, verificada no backend, e chega-lhe DIRETAMENTE, nunca através
 * deste proxy. Torná-lo público aqui abriria um `POST` sem sessão e sem CSRF
 * a atravessar o BFF. Filtrar por `GET` já o exclui estruturalmente; se um
 * endpoint público não-GET aparecer no futuro no contrato, quem alargar esta
 * lista tem de decidir conscientemente se deve mesmo atravessar o proxy do
 * BFF ou ser servido de outra forma (como o webhook).
 */

/** Segmentos de `/v1/providers/{x}` que são caminhos LITERAIS distintos no
 * contrato (`/v1/providers/me`, `/v1/providers/me/requests`), não instâncias
 * do parâmetro `{providerId}` — e por isso continuam a exigir sessão. */
const PROVIDER_RESERVED_SEGMENTS = new Set(['me']);

export function isPublicApiEndpoint(method: string, path: string): boolean {
  if (method !== 'GET') return false;

  if (path === '/v1/categories') return true;
  if (path === '/v1/subscription-plans') return true;
  if (path === '/v1/app/version-status') return true;
  if (path === '/v1/search/providers') return true;

  const providerMatch = /^\/v1\/providers\/([^/]+)$/.exec(path);
  if (providerMatch && !PROVIDER_RESERVED_SEGMENTS.has(providerMatch[1])) return true;

  const providerReviewsMatch = /^\/v1\/providers\/([^/]+)\/reviews$/.exec(path);
  if (providerReviewsMatch && !PROVIDER_RESERVED_SEGMENTS.has(providerReviewsMatch[1])) return true;

  return false;
}
