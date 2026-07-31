import { describe, expect, it } from 'vitest';
import { isPublicApiEndpoint } from '../src/publicEndpoints.js';

describe('isPublicApiEndpoint — allowlist por MÉTODO + CAMINHO (não por prefixo)', () => {
  it.each([
    ['GET', '/v1/categories'],
    ['GET', '/v1/subscription-plans'],
    ['GET', '/v1/app/version-status'],
    ['GET', '/v1/search/providers'],
    ['GET', '/v1/providers/prov-123'],
    ['GET', '/v1/providers/prov-123/reviews'],
  ])('%s %s é público', (method, path) => {
    expect(isPublicApiEndpoint(method, path)).toBe(true);
  });

  it.each([
    // A armadilha do prefixo: /v1/providers/me É um caminho literal distinto
    // no contrato, não uma instância de {providerId}. Um
    // `path.startsWith('/v1/providers')` diria "público" aqui — errado.
    ['GET', '/v1/providers/me'],
    ['PUT', '/v1/providers/me'],
    ['GET', '/v1/providers/me/requests'],
    // Método diferente no mesmo caminho de um endpoint público não o torna público.
    ['POST', '/v1/categories'],
    ['PUT', '/v1/categories'],
    ['DELETE', '/v1/categories'],
    // Endpoints de domínio comuns, sempre protegidos.
    ['GET', '/v1/requests'],
    ['POST', '/v1/requests'],
    ['GET', '/v1/proposals/me'],
    ['GET', '/v1/subscriptions/me'],
    ['GET', '/v1/conversations'],
    // O webhook de pagamentos tem `security: []` no contrato mas NUNCA passa
    // por este proxy (autenticado pela assinatura do gateway, chega
    // diretamente ao backend) — não é público no sentido deste allowlist.
    ['POST', '/v1/webhooks/payments/stripe'],
  ])('%s %s NÃO é público', (method, path) => {
    expect(isPublicApiEndpoint(method, path)).toBe(false);
  });
});
