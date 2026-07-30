import { randomUUID } from 'node:crypto';
import { Router, type Request } from 'express';
import * as client from 'openid-client';
import type { Config } from '../config.js';
import {
  OIDC_TRANSIT_COOKIE,
  SESSION_COOKIE,
  clearOidcTransitCookie,
  clearSessionCookie,
  readCookie,
  setOidcTransitCookie,
  setSessionCookie,
} from '../cookies.js';
import { isAllowedRealmRole, KeycloakAdminClient, KeycloakAdminError } from '../keycloakAdmin.js';
import { withNormalizedTiming } from '../loginTiming.js';
import { genericPasswordPolicyProblem, validatePassword } from '../passwordPolicy.js';
import { AuthAttemptLimiter } from '../rateLimit.js';
import { sendProblem } from '../problemDetails.js';
import { signPayload, verifyPayload } from '../signedCookie.js';
import type { SessionRecord, SessionStore } from '../session.js';

interface OidcTransitPayload {
  state: string;
  nonce: string;
  codeVerifier: string;
  /** Caminho relativo na SPA para onde voltar após o login. Nunca uma URL absoluta — evita open redirect. */
  returnTo: string;
}

export interface AuthDeps {
  config: Config;
  oidcConfig: client.Configuration;
  sessions: SessionStore;
  /** Injetável nos testes; por omissão constrói um cliente real. */
  adminClient?: KeycloakAdminClient;
  loginLimiter?: AuthAttemptLimiter;
  registerLimiter?: AuthAttemptLimiter;
}

const SCOPE = 'openid profile email';
const DIRECT_GRANT_SCOPE = 'openid profile email';

/**
 * Só aceita caminhos relativos internos como destino pós-login/logout —
 * nunca uma URL absoluta (open redirect). Browsers normalizam `\` para `/`
 * em URLs, por isso `/\evil.com` ou `\/evil.com` viram, na prática,
 * `//evil.com` (protocol-relative) — a mesma classe de bypass do
 * CVE-2025-68470 no react-router. Normaliza antes de validar.
 */
export function sanitizeReturnTo(value: unknown): string {
  if (typeof value !== 'string' || value.length === 0) {
    return '/';
  }
  const normalized = value.replace(/\\/g, '/');
  if (!normalized.startsWith('/') || normalized.startsWith('//')) {
    return '/';
  }
  return value;
}

function clientIp(req: Request): string {
  return req.ip ?? req.socket.remoteAddress ?? 'unknown';
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function splitName(name: string): { firstName: string; lastName: string } {
  const trimmed = name.trim().replace(/\s+/g, ' ');
  const spaceIndex = trimmed.indexOf(' ');
  if (spaceIndex === -1) {
    return { firstName: trimmed, lastName: trimmed };
  }
  return { firstName: trimmed.slice(0, spaceIndex), lastName: trimmed.slice(spaceIndex + 1) };
}

function publicUser(session: SessionRecord) {
  return {
    sub: session.user.sub,
    email: session.user.email,
    username: session.user.username,
    roles: session.user.roles,
  };
}

/** Erro normalizado de uma tentativa de login falhada — nunca chega ao corpo da resposta, só a logs. */
class LoginAttemptFailed extends Error {
  constructor(readonly reason: string) {
    super('login-attempt-failed');
  }
}

export function createAuthRouter({
  config,
  oidcConfig,
  sessions,
  adminClient = new KeycloakAdminClient(config, oidcConfig),
  loginLimiter = new AuthAttemptLimiter(
    config.authRateLimit.windowMs,
    config.authRateLimit.maxPerIp,
    config.authRateLimit.maxPerEmail,
  ),
  registerLimiter = new AuthAttemptLimiter(
    config.authRateLimit.windowMs,
    config.authRateLimit.maxPerIp,
    config.authRateLimit.maxPerEmail,
  ),
}: AuthDeps): Router {
  const router = Router();
  const redirectUri = `${config.bffOrigin}/auth/callback`;

  /**
   * Executa o Direct Access Grant (ROPC, ADR-0012 D3) — nunca reencaminha a
   * resposta do Keycloak, só o resultado (tokens em sucesso, exceção
   * normalizada em falha). A password vive só neste pedido: lida do corpo
   * de `/auth/login`/`/auth/register`, passada aqui, descartada.
   */
  async function directGrant(email: string, password: string) {
    try {
      return await client.genericGrantRequest(
        oidcConfig,
        'password',
        { username: email, password, scope: DIRECT_GRANT_SCOPE },
      );
    } catch (error) {
      // Nunca regista a password. Regista só a classe de erro do Keycloak
      // (ex.: "invalid_grant"), útil em diagnóstico e sem PII.
      const reason = error instanceof client.ResponseBodyError ? error.error : 'network_error';
      throw new LoginAttemptFailed(reason);
    }
  }

  // --- POST /auth/register (ADR-0012 D2) --------------------------------

  router.post('/register', async (req, res) => {
    const correlationId = randomUUID();
    const body = (req.body ?? {}) as Record<string, unknown>;
    const email = typeof body.email === 'string' ? body.email.trim() : '';
    const password = typeof body.password === 'string' ? body.password : '';
    const name = typeof body.name === 'string' ? body.name.trim() : '';
    const role = body.role;

    const fieldErrors: string[] = [];
    if (!isNonEmptyString(email) || !EMAIL_RE.test(email)) fieldErrors.push('email');
    if (!isNonEmptyString(name)) fieldErrors.push('name');
    if (!isAllowedRealmRole(role)) fieldErrors.push('role');
    if (fieldErrors.length > 0) {
      sendProblem(res, 400, 'invalid-registration', 'Dados de registo inválidos.', undefined, {
        invalidFields: fieldErrors,
      });
      return;
    }
    if (!isAllowedRealmRole(role)) {
      // Inalcançável — `fieldErrors` já teria capturado isto acima. Guarda
      // redundante só para o TypeScript estreitar o tipo de `role` daqui em
      // diante, sem `as`.
      sendProblem(res, 400, 'invalid-registration', 'Dados de registo inválidos.');
      return;
    }

    const passwordViolations = validatePassword(password, email);
    if (passwordViolations.length > 0) {
      sendProblem(
        res,
        400,
        'weak-password',
        'A password não cumpre a política de segurança.',
        passwordViolations.map((v) => v.message).join(' '),
        { errors: passwordViolations },
      );
      return;
    }

    const rate = registerLimiter.check(clientIp(req), email);
    if (!rate.allowed) {
      res.setHeader('Retry-After', Math.ceil(rate.retryAfterMs / 1000).toString());
      sendProblem(res, 429, 'too-many-requests', 'Demasiados pedidos de registo. Tenta novamente mais tarde.');
      return;
    }

    const { firstName, lastName } = splitName(name);

    let created: { id: string };
    try {
      const result = await adminClient.createUser({ email, password, firstName, lastName });
      if (result.outcome === 'conflict') {
        // Divergência deliberada da anti-enumeração estrita (ADR-0012 D7.3):
        // no REGISTO, ao contrário do LOGIN, o utilizador precisa de saber
        // que o email já está em uso para poder agir (recuperar acesso,
        // usar outro email) — sem isso o formulário fica sem explicação
        // possível para um erro que o utilizador não causou. O oráculo que
        // isto abre (confirmar que um email tem conta) é aceite aqui e só
        // aqui; o LOGIN continua estritamente indistinguível (ver `/login`
        // abaixo) porque aí o mesmo oráculo permite credential stuffing
        // dirigido, o que o registo não permite por si só.
        sendProblem(res, 409, 'email-already-registered', 'Já existe uma conta com este email.');
        return;
      }
      created = { id: result.id };
    } catch (error) {
      if (error instanceof KeycloakAdminError && error.status === 400) {
        const problem = genericPasswordPolicyProblem();
        sendProblem(res, 400, 'weak-password', 'A password não cumpre a política de segurança.', problem.message, {
          errors: [problem],
        });
        return;
      }
      // eslint-disable-next-line no-console
      console.error('[bff] auth.register: falha a criar utilizador no Keycloak', {
        correlationId,
        status: error instanceof KeycloakAdminError ? error.status : undefined,
      });
      sendProblem(res, 502, 'upstream-unavailable', 'Não foi possível concluir o registo. Tenta novamente.');
      return;
    }

    try {
      await adminClient.assignRealmRole(created.id, role);
    } catch (error) {
      // ROLLBACK: uma conta sem role é pior que inexistente (ADR-0012 D2).
      const deleted = await adminClient.deleteUser(created.id).catch(() => false);
      if (!deleted) {
        // O apagar também falhou — regista o órfão de forma correlacionável,
        // NUNCA com PII (nem email, nem nome): só o id opaco do Keycloak e o
        // correlation_id.
        // eslint-disable-next-line no-console
        console.error('[bff] auth.register: utilizador órfão sem role e sem rollback', {
          correlationId,
          keycloakUserId: created.id,
        });
      } else {
        // eslint-disable-next-line no-console
        console.warn('[bff] auth.register: atribuição de role falhou, rollback aplicado', {
          correlationId,
          keycloakUserId: created.id,
          error: error instanceof KeycloakAdminError ? error.status : undefined,
        });
      }
      sendProblem(res, 502, 'upstream-unavailable', 'Não foi possível concluir o registo. Tenta novamente.');
      return;
    }

    // Login imediato a seguir ao registo (ADR-0012 D2). Se falhar (não
    // deveria, dado `emailVerified:false` + `verifyEmail:false` no realm —
    // ver infra/README.md), o registo já está concluído: devolve sucesso
    // sem sessão para a SPA levar o utilizador ao ecrã de login manual, em
    // vez de mascarar como falha de registo.
    try {
      const tokens = await directGrant(email, password);
      const session = sessions.create({
        accessToken: tokens.access_token,
        refreshToken: tokens.refresh_token,
        idToken: tokens.id_token,
        expiresInSeconds: tokens.expiresIn() ?? 300,
      });
      setSessionCookie(res, config, session.id);
      res.status(201).json({ registered: true, session: true, user: publicUser(session) });
    } catch {
      // eslint-disable-next-line no-console
      console.warn('[bff] auth.register: login automático pós-registo falhou', { correlationId });
      res.status(201).json({ registered: true, session: false });
    }
  });

  // --- POST /auth/login (ADR-0012 D3, D7) --------------------------------

  router.post('/login', async (req, res) => {
    const correlationId = randomUUID();
    const body = (req.body ?? {}) as Record<string, unknown>;
    const email = typeof body.email === 'string' ? body.email.trim() : '';
    const password = typeof body.password === 'string' ? body.password : '';

    if (!isNonEmptyString(email) || !isNonEmptyString(password)) {
      sendProblem(res, 400, 'invalid-login', 'Email e password são obrigatórios.');
      return;
    }

    // Rate limiting ANTES de contactar o Keycloak (ADR-0012 D7.1) — o
    // Keycloak só vê o IP do BFF com Direct Access Grant, por isso a
    // proteção real tem de estar aqui. IP primeiro: um atacante que
    // percorre muitos emails esgota a SUA quota antes de gastar a de
    // nenhuma vítima.
    const rate = loginLimiter.check(clientIp(req), email);
    if (!rate.allowed) {
      res.setHeader('Retry-After', Math.ceil(rate.retryAfterMs / 1000).toString());
      sendProblem(res, 429, 'too-many-requests', 'Demasiadas tentativas de login. Tenta novamente mais tarde.');
      return;
    }

    try {
      const session = await withNormalizedTiming(
        async () => {
          const tokens = await directGrant(email, password);
          return sessions.create({
            accessToken: tokens.access_token,
            refreshToken: tokens.refresh_token,
            idToken: tokens.id_token,
            expiresInSeconds: tokens.expiresIn() ?? 300,
          });
        },
        config.loginTiming,
        {
          onEscapeValve: ({ elapsedMs }) => {
            // eslint-disable-next-line no-console
            console.warn('[bff] auth.login: válvula de escape do prazo fixo acionada (IdP lento?)', {
              correlationId,
              elapsedMs,
            });
          },
        },
        correlationId,
      );
      setSessionCookie(res, config, session.id);
      res.status(200).json({ authenticated: true, user: publicUser(session) });
    } catch (error) {
      // Resposta IDÊNTICA em corpo e em código de estado, seja "conta
      // inexistente" ou "password errada" — o `withNormalizedTiming` já
      // garantiu que também o tempo de resposta é indistinguível
      // (ADR-0012 D7.3/D7.4). O motivo real só vai para o log, sem PII.
      const reason = error instanceof LoginAttemptFailed ? error.reason : 'unknown';
      // eslint-disable-next-line no-console
      console.info('[bff] auth.login: tentativa falhada', { correlationId, reason });
      sendProblem(res, 401, 'invalid-credentials', 'Credenciais inválidas.');
    }
  });

  // --- POST /auth/logout (ADR-0012 D3) -----------------------------------
  //
  // Contrato NOVO: 204 sem corpo, nunca `logoutUrl` — o site deixou de
  // navegar para o Keycloak porque o utilizador nunca o vê (D1). Perde-se o
  // fim de sessão SSO no Keycloak (cookie `KEYCLOAK_IDENTITY` residual até
  // `ssoSessionIdleTimeout`); consequência aceite conscientemente (D3).

  router.post('/logout', async (req, res) => {
    const sessionId = readCookie(req, SESSION_COOKIE);
    const session = sessionId ? sessions.get(sessionId) : undefined;

    clearSessionCookie(res, config);
    if (!session) {
      res.status(204).end();
      return;
    }

    sessions.destroy(session.id);

    if (session.refreshToken) {
      // Best-effort: revogar o refresh token no Keycloak. Se falhar, a sessão
      // do BFF já está destruída de qualquer forma.
      await client.tokenRevocation(oidcConfig, session.refreshToken).catch(() => undefined);
    }

    res.status(204).end();
  });

  // --- Fluxo de regresso Authorization Code + PKCE (ADR-0012 D1/D10) -----
  //
  // Só existe se `config.legacyOidcFlow.enabled` for `true` (omissão:
  // `false`). Nunca reativável por cabeçalho nem parâmetro de pedido — é
  // decidido inteiramente no arranque do processo, a partir de
  // `LEGACY_OIDC_FLOW_ENABLED` (config.ts). Com a flag desligada, `GET
  // /auth/login` e `GET /auth/callback` simplesmente não existem (404) — o
  // `POST /auth/login` acima não colide, são métodos diferentes na mesma
  // rota.
  if (config.legacyOidcFlow.enabled) {
    router.get('/login', (req, res) => {
      const codeVerifier = client.randomPKCECodeVerifier();
      const state = client.randomState();
      const nonce = client.randomNonce();
      const returnTo = sanitizeReturnTo(req.query.returnTo);

      client
        .calculatePKCECodeChallenge(codeVerifier)
        .then((codeChallenge) => {
          const transit: OidcTransitPayload = { state, nonce, codeVerifier, returnTo };
          setOidcTransitCookie(res, config, signPayload(transit, config.cookies.signingSecret));

          const authorizationUrl = client.buildAuthorizationUrl(oidcConfig, {
            redirect_uri: redirectUri,
            scope: SCOPE,
            code_challenge: codeChallenge,
            code_challenge_method: 'S256',
            state,
            nonce,
          });
          res.redirect(authorizationUrl.toString());
        })
        .catch(() => {
          sendProblem(res, 502, 'upstream-unavailable', 'Não foi possível iniciar o login com o Keycloak.');
        });
    });

    router.get('/callback', async (req, res) => {
      const transitCookie = readCookie(req, OIDC_TRANSIT_COOKIE);
      const transit = transitCookie
        ? verifyPayload<OidcTransitPayload>(transitCookie, config.cookies.signingSecret)
        : undefined;

      if (!transit) {
        clearOidcTransitCookie(res, config);
        res.redirect(`${config.appOrigin}/login?error=callback_expired`);
        return;
      }

      clearOidcTransitCookie(res, config);

      try {
        const currentUrl = new URL(req.originalUrl, config.bffOrigin);
        const tokens = await client.authorizationCodeGrant(oidcConfig, currentUrl, {
          pkceCodeVerifier: transit.codeVerifier,
          expectedState: transit.state,
          expectedNonce: transit.nonce,
        });

        const session = sessions.create({
          accessToken: tokens.access_token,
          refreshToken: tokens.refresh_token,
          idToken: tokens.id_token,
          expiresInSeconds: tokens.expiresIn() ?? 300,
        });

        setSessionCookie(res, config, session.id);
        res.redirect(`${config.appOrigin}${transit.returnTo}`);
      } catch {
        res.redirect(`${config.appOrigin}/login?error=callback_failed`);
      }
    });
  }

  router.get('/me', (req, res) => {
    const sessionId = readCookie(req, SESSION_COOKIE);
    const session = sessionId ? sessions.get(sessionId) : undefined;
    if (!session) {
      res.json({ authenticated: false });
      return;
    }
    res.json({ authenticated: true, user: publicUser(session) });
  });

  return router;
}
