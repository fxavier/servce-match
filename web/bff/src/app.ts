import express, { type Express } from 'express';
import type * as client from 'openid-client';
import type { Config } from './config.js';
import { ensureCsrfCookie, verifyCsrf } from './csrf.js';
import { createApiProxyRouter } from './proxy.js';
import { createAuthRouter } from './routes/auth.js';
import { SessionStore } from './session.js';

export interface AppDeps {
  config: Config;
  oidcConfig: client.Configuration;
  sessions?: SessionStore;
}

/**
 * Monta a app Express. Separado de `server.ts` (que faz `listen`) para que
 * os testes de integração (supertest) consigam instanciar a app sem abrir
 * uma porta de rede real.
 */
export function createApp({ config, oidcConfig, sessions }: AppDeps): Express {
  const app = express();
  app.disable('x-powered-by');
  const sessionStore = sessions ?? new SessionStore(config.session.absoluteTtlSeconds);

  // Número explícito de saltos de proxy confiável (ADR-0012 D7.2). NUNCA
  // `true` — isso confiaria em X-Forwarded-For vindo de qualquer origem e
  // tornaria o rate limiting de /auth/login e /auth/register contornável
  // com um cabeçalho forjado. `0` (omissão) ignora XFF por completo.
  app.set('trust proxy', config.trustProxyHops);

  // Sem CORS: o BFF nunca aceita pedidos com credenciais de outra origem.
  // Em desenvolvimento, o Vite faz proxy de /api e /auth para aqui, pelo que
  // o browser trata tudo como same-origin (ver web/app/vite.config.ts).
  app.use((_req, res, next) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Content-Security-Policy', "default-src 'none'");
    next();
  });

  app.use(ensureCsrfCookie(config));
  app.use(verifyCsrf);

  app.get('/healthz', (_req, res) => res.status(200).json({ status: 'ok' }));

  // JSON só para /auth (registo/login recebem { email, password, ... } no
  // corpo). /api continua em express.raw — o BFF nunca reinterpreta o
  // payload do domínio (ver comentário abaixo).
  app.use('/auth', express.json({ limit: '16kb' }));
  app.use('/auth', createAuthRouter({ config, oidcConfig, sessions: sessionStore }));

  // Corpo em bruto: o BFF reencaminha o payload tal como chega, sem o
  // reinterpretar — o contrato (docs/api/openapi.yaml) é responsabilidade do
  // backend, não do BFF.
  app.use('/api', express.raw({ type: () => true, limit: '10mb' }));
  app.use('/api', createApiProxyRouter({ config, oidcConfig, sessions: sessionStore }));

  return app;
}
