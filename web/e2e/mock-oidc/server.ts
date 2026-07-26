import { Events, OAuth2Server } from 'oauth2-mock-server';

/**
 * Keycloak falso para o E2E: implementa Authorization Code + PKCE via
 * `/authorize` → redirect automático (sem UI de login, é só para
 * automação) e `/token`. O BFF fala com isto exatamente como falaria com o
 * Keycloak real — o que está a ser testado é o *nosso* código (BFF +ADR-0002),
 * não o Keycloak.
 */
async function main(): Promise<void> {
  const port = Number(process.env.PORT ?? 8092);
  const server = new OAuth2Server();
  await server.issuer.keys.generate('RS256');

  server.service.on(Events.BeforeTokenSigning, (token) => {
    token.payload.sub = 'e2e-customer-1';
    token.payload.email = 'customer.test@servimatch.pt';
    token.payload.preferred_username = 'customer.test';
    token.payload.realm_access = { roles: ['CUSTOMER'] };
    token.payload.aud ??= 'servimatch-backend';
  });

  await server.start(port, 'localhost');
  // eslint-disable-next-line no-console
  console.log(`[mock-oidc] a ouvir em ${server.issuer.url}`);
}

main().catch((error: unknown) => {
  // eslint-disable-next-line no-console
  console.error('[mock-oidc] falha no arranque', error);
  process.exit(1);
});
