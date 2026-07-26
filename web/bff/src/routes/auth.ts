import { Router } from 'express';
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
import { signPayload, verifyPayload } from '../signedCookie.js';
import type { SessionStore } from '../session.js';
import { sendProblem } from '../problemDetails.js';

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
}

const SCOPE = 'openid profile email';

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

export function createAuthRouter({ config, oidcConfig, sessions }: AuthDeps): Router {
  const router = Router();
  const redirectUri = `${config.bffOrigin}/auth/callback`;

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

  router.get('/me', (req, res) => {
    const sessionId = readCookie(req, SESSION_COOKIE);
    const session = sessionId ? sessions.get(sessionId) : undefined;
    if (!session) {
      res.json({ authenticated: false });
      return;
    }
    res.json({
      authenticated: true,
      user: {
        sub: session.user.sub,
        email: session.user.email,
        username: session.user.username,
        roles: session.user.roles,
      },
    });
  });

  router.post('/logout', async (req, res) => {
    const sessionId = readCookie(req, SESSION_COOKIE);
    const session = sessionId ? sessions.get(sessionId) : undefined;

    clearSessionCookie(res, config);
    if (!session) {
      res.json({ logoutUrl: config.appOrigin });
      return;
    }

    sessions.destroy(session.id);

    if (session.refreshToken) {
      // Best-effort: revogar o refresh token no Keycloak. Se falhar, a sessão
      // do BFF já está destruída de qualquer forma — o utilizador não fica
      // "autenticado" neste cliente mesmo que a revogação remota falhe.
      await client.tokenRevocation(oidcConfig, session.refreshToken).catch(() => undefined);
    }

    // Termina também a sessão SSO no Keycloak (CLAUDE.md §4): a SPA deve
    // navegar para este URL depois de receber a resposta.
    const endSessionUrl = client.buildEndSessionUrl(oidcConfig, {
      post_logout_redirect_uri: config.appOrigin,
      ...(session.idToken ? { id_token_hint: session.idToken } : {}),
    });
    res.json({ logoutUrl: endSessionUrl.toString() });
  });

  return router;
}
