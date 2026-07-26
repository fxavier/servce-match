import * as client from 'openid-client';
import type { Config } from './config.js';

/**
 * Descoberta OIDC (ADR-0002): o BFF é o único componente web que fala
 * diretamente com o Keycloak. A SPA nunca vê o `issuer`, o `client_secret`
 * nem qualquer token.
 */
export async function discoverKeycloak(config: Config): Promise<client.Configuration> {
  const issuer = new URL(config.keycloak.issuerUri);
  // O ambiente local corre o Keycloak em http://localhost:8081 (infra/README.md).
  // oauth4webapi recusa HTTP por omissão; só relaxamos quando o issuer não é
  // https — em produção o issuer é sempre https, isto nunca se aplica lá.
  const execute = issuer.protocol === 'http:' ? [client.allowInsecureRequests] : [];
  return client.discovery(issuer, config.keycloak.clientId, config.keycloak.clientSecret, undefined, {
    execute,
  });
}

export { client };
