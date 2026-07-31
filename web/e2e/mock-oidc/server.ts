import { randomUUID } from 'node:crypto';
import type { ServerResponse } from 'node:http';
import express from 'express';
import { Events, OAuth2Server, type TokenRequestIncomingMessage } from 'oauth2-mock-server';
import { SEED_ADMIN_EMAIL } from '../fixtures.js';

/**
 * Keycloak falso para o E2E (ADR-0012). Substitui o antigo Authorization
 * Code + PKCE — o utilizador nunca vê este servidor, só o BFF fala com ele:
 *
 * - `POST /realms/servimatch/protocol/openid-connect/token` com
 *   `grant_type=password` (login, D3) e `grant_type=client_credentials`
 *   (*service account* do registo, D2) — ambos suportados nativamente pelo
 *   `oauth2-mock-server`.
 * - `POST /revoke` (logout) — idem.
 * - Admin REST API do registo (`POST/DELETE .../users`,
 *   `GET/POST .../role-mappings/realm...`) — o `oauth2-mock-server` não
 *   simula isto (é específico do Keycloak, não OAuth2 genérico), por isso
 *   corre aqui um Express pequeno, num porto próprio
 *   (`KEYCLOAK_ADMIN_API_BASE_URL`), a implementar só os quatro pedidos que
 *   `web/bff/src/keycloakAdmin.ts` faz de facto — não uma réplica geral do
 *   Keycloak.
 *
 * Os dois servidores partilham o mesmo `directory` em memória: um
 * utilizador criado pela Admin REST API fica disponível para o `password`
 * grant seguinte atribuir `email`/`roles` corretos ao token — é assim que
 * "registar → login imediato" (D2) fica coerente de ponta a ponta no teste.
 *
 * Limitação aceite: este mock não valida a password no `password` grant
 * (o `oauth2-mock-server` não o faz) — não é o que este E2E testa. Isso é
 * testado ao nível de unidade/integração no BFF, contra Keycloak real
 * (Testcontainers, ver ARQUITETURA.md §16).
 */

interface DirectoryUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
}

const directory = new Map<string, DirectoryUser>(); // id -> user
const byEmail = new Map<string, string>(); // email -> id

/**
 * Conta `ADMIN` semeada diretamente no diretório em memória, fora do fluxo
 * `POST /auth/register` (que só aceita `role` `CUSTOMER`/`PROVIDER` —
 * `web/bff/src/keycloakAdmin.ts::isAllowedRealmRole` — administradores não
 * se auto-registam, CLAUDE.md §4). Equivalente, para este mock, a um
 * administrador criado fora de banda no realm real; existe só para que
 * `web/e2e/tests/admin-approval.spec.ts` consiga autenticar-se por
 * `POST /auth/login` (o mock não valida password, ver o comentário do
 * módulo).
 */
const SEED_ADMIN_USER_ID = 'e2e-admin-seed-1';
directory.set(SEED_ADMIN_USER_ID, {
  id: SEED_ADMIN_USER_ID,
  email: SEED_ADMIN_EMAIL,
  firstName: 'Admin',
  lastName: 'E2E',
  roles: ['ADMIN'],
});
byEmail.set(SEED_ADMIN_EMAIL, SEED_ADMIN_USER_ID);

function sendJson(res: ServerResponse, status: number, body: unknown): void {
  const content = JSON.stringify(body);
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Content-Length', Buffer.byteLength(content));
  res.end(content);
}

/**
 * `web/bff/src/config.ts::deriveAdminApiBaseUrl` exige que
 * `KEYCLOAK_ISSUER_URI` tenha a forma `.../realms/{realm}` — mesmo quando
 * `KEYCLOAK_ADMIN_API_BASE_URL` é definida explicitamente, porque o
 * `fallback` de `optional()` é avaliado antes de a função saber que não
 * precisa dele. Isto força um `issuer` com um caminho não-raiz
 * (`/realms/servimatch`).
 *
 * O `oauth2-mock-server`, por outro lado, publica os endpoints nativos
 * (`/token`, `/jwks`, `/revoke`) sempre relativos à RAIZ do servidor,
 * combinados com `issuer.url` por concatenação simples (`urlCombine`) — se
 * `issuer.url` tivesse o caminho `/realms/servimatch`, os URLs anunciados
 * no documento de descoberta deixariam de bater certo com as rotas
 * realmente registadas (ficariam com o prefixo duplicado ou em falta).
 *
 * Resolução: `issuer.url` fica na raiz (omissão do `oauth2-mock-server`,
 * como no fluxo antigo) — os endpoints nativos continuam corretos e
 * funcionais. Regista-se **um** documento de descoberta adicional, à mão,
 * no caminho exato que o `openid-client` vai pedir a partir de
 * `KEYCLOAK_ISSUER_URI` (`/realms/servimatch/.well-known/openid-configuration`),
 * com `issuer` a repetir esse mesmo valor (para passar a verificação de
 * correspondência do `openid-client`) mas apontando os restantes campos
 * para os endpoints nativos, reais, na raiz.
 */
function registerRealmScopedDiscoveryDocument(server: OAuth2Server, issuerWithRealmPath: string): void {
  const rootIssuer = server.issuer.url;
  server.service.addRoute('GET', '/realms/servimatch/.well-known/openid-configuration', (_req, res) => {
    sendJson(res, 200, {
      issuer: issuerWithRealmPath,
      token_endpoint: `${rootIssuer}/token`,
      jwks_uri: `${rootIssuer}/jwks`,
      revocation_endpoint: `${rootIssuer}/revoke`,
      authorization_endpoint: `${rootIssuer}/authorize`,
      userinfo_endpoint: `${rootIssuer}/userinfo`,
      end_session_endpoint: `${rootIssuer}/endsession`,
      introspection_endpoint: `${rootIssuer}/introspect`,
      token_endpoint_auth_methods_supported: ['none', 'client_secret_basic', 'client_secret_post'],
      grant_types_supported: ['client_credentials', 'authorization_code', 'password'],
      response_types_supported: ['code'],
      response_modes_supported: ['query'],
      id_token_signing_alg_values_supported: ['RS256'],
      token_endpoint_auth_signing_alg_values_supported: ['RS256'],
      subject_types_supported: ['public'],
      code_challenge_methods_supported: ['plain', 'S256'],
    });
  });
}

async function startOidcServer(port: number): Promise<OAuth2Server> {
  const server = new OAuth2Server();
  await server.issuer.keys.generate('RS256');

  // Só conhecido depois de `.start()` (o `oauth2-mock-server` deriva
  // `issuer.url` do host/porto reais nesse momento) — por isso o handler de
  // assinatura lê `realmIssuer` de uma variável fechada, preenchida a
  // seguir ao arranque, em vez de a recalcular a cada token.
  let realmIssuer = '';

  server.service.on(Events.BeforeTokenSigning, (token, req: TokenRequestIncomingMessage) => {
    // `issuer.buildToken()` assina sempre com `iss: this.issuer.url` (a
    // raiz do servidor — ver racional em `registerRealmScopedDiscoveryDocument`).
    // O `openid-client` valida o `iss` do token contra o `issuer` do
    // documento de descoberta (`.../realms/servimatch`) sempre que a
    // resposta inclui um `id_token` — sem esta reescrita, TODO grant que
    // emita `id_token` (password, client_credentials não emite) falha com
    // "unexpected JWT iss claim value", mesmo com o `access_token`
    // perfeitamente válido.
    if (realmIssuer) token.payload.iss = realmIssuer;

    if (req.body.grant_type !== 'password' || typeof token.payload.sub !== 'string') return;

    // Neste ponto `token.payload.sub` é o `username` submetido (o email —
    // é o que `directGrant()` no BFF envia) para o ACCESS token; no
    // `id_token` continua a ser o valor por omissão do mock ("johndoe"),
    // irrelevante — só o access_token é decodificado em
    // `web/bff/src/session.ts`. Substitui-se pelo `sub` estável do
    // utilizador (como no Keycloak real, nunca o email) e acrescentam-se as
    // claims que essa decodificação lê.
    const email = token.payload.sub;
    const userId = byEmail.get(email);
    const user = userId ? directory.get(userId) : undefined;
    if (!user) return; // Login sem registo prévio não é exercitado por este E2E.

    token.payload.sub = user.id;
    token.payload.email = user.email;
    token.payload.preferred_username = user.email;
    token.payload.realm_access = { roles: user.roles };
    token.payload.aud ??= 'servimatch-bff';
  });

  await server.start(port, 'localhost');
  realmIssuer = `${server.issuer.url}/realms/servimatch`;
  registerRealmScopedDiscoveryDocument(server, realmIssuer);
  return server;
}

function startAdminApi(port: number): Promise<void> {
  const app = express();
  app.use(express.json());

  app.post('/users', (req, res) => {
    const body = req.body as {
      email?: string;
      firstName?: string;
      lastName?: string;
    };
    const email = body.email ?? '';
    if (byEmail.has(email)) {
      res.status(409).end();
      return;
    }
    const id = randomUUID();
    directory.set(id, { id, email, firstName: body.firstName ?? '', lastName: body.lastName ?? '', roles: [] });
    byEmail.set(email, id);
    res.status(201).setHeader('Location', `${req.protocol}://${req.get('host')}/users/${id}`).end();
  });

  app.get('/users/:id/role-mappings/realm/available', (req, res) => {
    if (!directory.has(req.params.id)) {
      res.status(404).end();
      return;
    }
    // Simplificação deliberada: devolve sempre as duas roles de domínio
    // como "disponíveis", independentemente do que já foi atribuído — o
    // service account real só tem `manage-users`/`view-users`, não
    // `view-realm`, e é exatamente esta chamada que o contorna (ver
    // comentário em `keycloakAdmin.ts`).
    res.json([
      { id: 'role-customer', name: 'CUSTOMER' },
      { id: 'role-provider', name: 'PROVIDER' },
    ]);
  });

  app.post('/users/:id/role-mappings/realm', (req, res) => {
    const user = directory.get(req.params.id);
    if (!user) {
      res.status(404).end();
      return;
    }
    const roles = (req.body as { name?: string }[] | undefined) ?? [];
    for (const role of roles) {
      if (role.name && !user.roles.includes(role.name)) user.roles.push(role.name);
    }
    res.status(204).end();
  });

  app.delete('/users/:id', (req, res) => {
    const user = directory.get(req.params.id);
    if (user) {
      directory.delete(user.id);
      byEmail.delete(user.email);
    }
    res.status(204).end();
  });

  app.get('/healthz', (_req, res) => sendJson(res, 200, { status: 'ok' }));

  return new Promise((resolve) => {
    app.listen(port, 'localhost', () => resolve());
  });
}

async function main(): Promise<void> {
  const oidcPort = Number(process.env.PORT ?? 8092);
  const adminPort = Number(process.env.ADMIN_PORT ?? 8094);

  const oidcServer = await startOidcServer(oidcPort);
  await startAdminApi(adminPort);

  // eslint-disable-next-line no-console
  console.log(`[mock-oidc] a ouvir em ${oidcServer.issuer.url}`);
  // eslint-disable-next-line no-console
  console.log(`[mock-oidc] admin REST API a ouvir em http://localhost:${adminPort}`);
}

main().catch((error: unknown) => {
  // eslint-disable-next-line no-console
  console.error('[mock-oidc] falha no arranque', error);
  process.exit(1);
});
