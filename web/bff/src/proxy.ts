import { Router, type NextFunction, type Request, type Response } from 'express';
import * as client from 'openid-client';
import type { Config } from './config.js';
import { SESSION_COOKIE, clearSessionCookie, readCookie, setSessionCookie } from './cookies.js';
import { sendBadGateway, sendUnauthenticated } from './problemDetails.js';
import type { SessionStore } from './session.js';

export interface ProxyDeps {
  config: Config;
  oidcConfig: client.Configuration;
  sessions: SessionStore;
}

// Cabeçalhos do pedido do browser que fazem sentido reencaminhar ao backend.
// Nunca reencaminhamos `cookie` (é a sessão do BFF, não do backend) nem
// `host`/`connection` (específicos deste salto).
const FORWARD_REQUEST_HEADERS = ['content-type', 'idempotency-key', 'accept', 'accept-language'];

declare module 'express-serve-static-core' {
  interface Request {
    sessionRecord?: import('./session.js').SessionRecord;
  }
}

/**
 * Exige sessão válida e garante que o access_token usado a seguir não está
 * prestes a expirar — silent refresh no BFF, nunca visível ao browser
 * (ADR-0002: o browser não vê tokens, só o cookie de sessão opaco).
 */
export function requireSession({ config, oidcConfig, sessions }: ProxyDeps) {
  return async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    const sessionId = readCookie(req, SESSION_COOKIE);
    const session = sessionId ? sessions.get(sessionId) : undefined;
    if (!session) {
      sendUnauthenticated(res, 'Sem sessão ativa. Autentica-te em /auth/login.');
      return;
    }

    if (!sessions.isExpiringSoon(session)) {
      req.sessionRecord = session;
      next();
      return;
    }

    if (!session.refreshToken) {
      sessions.destroy(session.id);
      clearSessionCookie(res, config);
      sendUnauthenticated(res, 'Sessão expirada.');
      return;
    }

    try {
      const refreshed = await client.refreshTokenGrant(oidcConfig, session.refreshToken);
      const updated = sessions.update(session.id, {
        accessToken: refreshed.access_token,
        refreshToken: refreshed.refresh_token ?? session.refreshToken,
        idToken: refreshed.id_token ?? session.idToken,
        accessTokenExpiresAt: Date.now() + (refreshed.expiresIn() ?? 300) * 1000,
      });
      setSessionCookie(res, config, session.id);
      req.sessionRecord = updated;
      next();
    } catch {
      sessions.destroy(session.id);
      clearSessionCookie(res, config);
      sendUnauthenticated(res, 'Sessão expirada — a renovação falhou.');
    }
  };
}

export function createApiProxyRouter(deps: ProxyDeps): Router {
  const router = Router();

  router.use(requireSession(deps));

  router.use(async (req: Request, res: Response) => {
    const session = req.sessionRecord;
    if (!session) {
      // Não deve acontecer (requireSession já responde antes) — defensivo.
      sendUnauthenticated(res);
      return;
    }

    const targetPath = req.originalUrl.replace(/^\/api/, '');
    const targetUrl = `${deps.config.backendOrigin}${targetPath}`;

    const headers = new Headers();
    for (const name of FORWARD_REQUEST_HEADERS) {
      const value = req.header(name);
      if (value) headers.set(name, value);
    }
    headers.set('Authorization', `Bearer ${session.accessToken}`);

    const hasBody = !['GET', 'HEAD'].includes(req.method) && Buffer.isBuffer(req.body) && req.body.length > 0;

    try {
      const upstream = await fetch(targetUrl, {
        method: req.method,
        headers,
        body: hasBody ? req.body : undefined,
      });

      res.status(upstream.status);
      const contentType = upstream.headers.get('content-type');
      if (contentType) res.setHeader('Content-Type', contentType);
      const location = upstream.headers.get('location');
      if (location) res.setHeader('Location', location);

      const buffer = Buffer.from(await upstream.arrayBuffer());
      res.send(buffer);
    } catch {
      sendBadGateway(res, 'Falha de rede a contactar o backend.');
    }
  });

  return router;
}
