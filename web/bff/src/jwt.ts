/**
 * Descodifica (sem verificar assinatura) o payload de um JWT. Seguro aqui
 * porque o BFF recebe este token diretamente do token endpoint do Keycloak
 * por TLS (não de um cliente não confiável) — não há verificação de
 * assinatura a fazer neste ponto, só leitura de claims para derivar `sub` e
 * `roles` a expor ao frontend. O backend, esse sim, valida a assinatura via
 * JWKS a cada pedido (ele é o Resource Server, ver ADR-0002).
 */
export interface KeycloakAccessTokenClaims {
  sub: string;
  email?: string;
  preferred_username?: string;
  realm_access?: { roles?: string[] };
  exp: number;
  aud?: string | string[];
}

export function decodeJwtPayload<T>(jwt: string): T {
  const parts = jwt.split('.');
  if (parts.length !== 3) {
    throw new Error('Not a JWT (expected 3 segments)');
  }
  const json = Buffer.from(parts[1], 'base64url').toString('utf8');
  return JSON.parse(json) as T;
}
