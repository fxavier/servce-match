import { createHmac, timingSafeEqual } from 'node:crypto';

/**
 * Assina um payload JSON para viver num cookie transitório (o `state`/
 * `code_verifier`/`nonce` do PKCE durante o roundtrip até ao Keycloak). Não é
 * um JWT: é só HMAC sobre JSON, o suficiente para detetar adulteração num
 * valor que nunca é secreto por si (o segredo real é o `code_verifier`
 * combinado com o `code`, que só o BFF troca por tokens).
 */
export function signPayload(payload: unknown, secret: string): string {
  const json = Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
  const signature = createHmac('sha256', secret).update(json).digest('base64url');
  return `${json}.${signature}`;
}

export function verifyPayload<T>(token: string, secret: string): T | undefined {
  const parts = token.split('.');
  if (parts.length !== 2) return undefined;
  const [json, signature] = parts;
  const expected = createHmac('sha256', secret).update(json).digest('base64url');
  const a = Buffer.from(signature);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !timingSafeEqual(a, b)) return undefined;
  try {
    return JSON.parse(Buffer.from(json, 'base64url').toString('utf8')) as T;
  } catch {
    return undefined;
  }
}
