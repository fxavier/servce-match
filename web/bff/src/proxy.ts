import { Router, type NextFunction, type Request, type Response } from 'express';
import * as client from 'openid-client';
import type { Config } from './config.js';
import { SESSION_COOKIE, clearSessionCookie, readCookie, setSessionCookie } from './cookies.js';
import { isPublicApiEndpoint } from './publicEndpoints.js';
import { sendBadGateway, sendUnauthenticated } from './problemDetails.js';
import type { SessionRecord, SessionStore } from './session.js';

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

type SessionOutcome =
  | { kind: 'none' }
  | { kind: 'valid'; session: SessionRecord }
  | { kind: 'invalid'; detail: string };

/**
 * Único ponto que lê o cookie de sessão, verifica expiração do access_token
 * e faz o silent refresh (ADR-0012). `requireSession` (endpoints protegidos)
 * e `attachOptionalSession` (endpoints públicos do contrato) partilham esta
 * lógica — só divergem no que fazem quando o resultado não é `valid`.
 */
async function resolveSession(
  { config, oidcConfig, sessions }: ProxyDeps,
  req: Request,
  res: Response,
): Promise<SessionOutcome> {
  const sessionId = readCookie(req, SESSION_COOKIE);
  const session = sessionId ? sessions.get(sessionId) : undefined;
  if (!session) {
    return { kind: 'none' };
  }

  if (!sessions.isExpiringSoon(session)) {
    return { kind: 'valid', session };
  }

  if (!session.refreshToken) {
    sessions.destroy(session.id);
    clearSessionCookie(res, config);
    return { kind: 'invalid', detail: 'Sessão expirada.' };
  }

  try {
    // `refreshOnce` deduplica renovações concorrentes do mesmo
    // `session.id`: dois pedidos `/api/**` em paralelo com o access token
    // quase a expirar partilham a mesma chamada a `refreshTokenGrant` em
    // vez de cada um tentar rodar o refresh token antigo (o segundo
    // falharia sempre que o Keycloak rode o refresh token na resposta).
    const updated = await sessions.refreshOnce(session.id, async (current) => {
      const refreshed = await client.refreshTokenGrant(oidcConfig, current.refreshToken!);
      return {
        accessToken: refreshed.access_token,
        refreshToken: refreshed.refresh_token ?? current.refreshToken,
        idToken: refreshed.id_token ?? current.idToken,
        accessTokenExpiresAt: Date.now() + (refreshed.expiresIn() ?? 300) * 1000,
      };
    });
    if (!updated) {
      sessions.destroy(session.id);
      clearSessionCookie(res, config);
      return { kind: 'invalid', detail: 'Sessão expirada.' };
    }
    setSessionCookie(res, config, session.id);
    return { kind: 'valid', session: updated };
  } catch {
    sessions.destroy(session.id);
    clearSessionCookie(res, config);
    return { kind: 'invalid', detail: 'Sessão expirada — a renovação falhou.' };
  }
}

/**
 * Exige sessão válida. Aplica-se a todo o `/api/**` EXCETO aos endpoints da
 * allowlist `isPublicApiEndpoint` (ver publicEndpoints.ts), que usam
 * `attachOptionalSession` em vez desta.
 */
export function requireSession(deps: ProxyDeps) {
  return async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    const outcome = await resolveSession(deps, req, res);
    if (outcome.kind === 'none') {
      sendUnauthenticated(res, 'Sem sessão ativa. Autentica-te em /auth/login.');
      return;
    }
    if (outcome.kind === 'invalid') {
      sendUnauthenticated(res, outcome.detail);
      return;
    }
    req.sessionRecord = outcome.session;
    next();
  };
}

/**
 * Sessão OPCIONAL — nunca ignorada. Só usada nos endpoints públicos do
 * contrato (`isPublicApiEndpoint`). Um pedido sem cookie, ou com sessão
 * expirada cuja renovação falha, segue como anónimo (sem `Authorization` no
 * proxy a seguir) em vez de 401: um cookie velho não pode partir a homepage.
 * Um pedido com sessão VÁLIDA continua a levar o Bearer — alguns endpoints
 * públicos devolvem mais informação a quem está autenticado, por isso a
 * regra é "sessão opcional", nunca "sessão ignorada".
 */
export function attachOptionalSession(deps: ProxyDeps) {
  return async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    const outcome = await resolveSession(deps, req, res);
    if (outcome.kind === 'valid') {
      req.sessionRecord = outcome.session;
    }
    next();
  };
}

export function createApiProxyRouter(deps: ProxyDeps): Router {
  const router = Router();

  const requireSessionMw = requireSession(deps);
  const optionalSessionMw = attachOptionalSession(deps);

  router.use((req: Request, res: Response, next: NextFunction) => {
    if (isPublicApiEndpoint(req.method, req.path)) {
      optionalSessionMw(req, res, next);
      return;
    }
    requireSessionMw(req, res, next);
  });

  router.use(async (req: Request, res: Response) => {
    const session = req.sessionRecord;
    if (!session && !isPublicApiEndpoint(req.method, req.path)) {
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
    // Pedido público sem sessão (visitante anónimo): segue sem Authorization
    // — nunca reencaminhamos um Bearer que não existe, e o backend já sabe
    // servir este endpoint sem token (é o mesmo `security: []`).
    if (session) {
      headers.set('Authorization', `Bearer ${session.accessToken}`);
    }

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
