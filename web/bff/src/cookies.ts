import { parse, serialize, type SerializeOptions } from 'cookie';
import type { Request, Response } from 'express';
import type { Config } from './config.js';

export const SESSION_COOKIE = 'sm_sid';
export const OIDC_TRANSIT_COOKIE = 'sm_oidc';
export const CSRF_COOKIE = 'sm_csrf';

export function readCookie(req: Request, name: string): string | undefined {
  const header = req.headers.cookie;
  if (!header) return undefined;
  return parse(header)[name];
}

function baseOptions(config: Config, maxAgeSeconds: number, httpOnly: boolean): SerializeOptions {
  return {
    httpOnly,
    secure: config.cookies.secure,
    sameSite: 'lax',
    path: '/',
    maxAge: maxAgeSeconds,
  };
}

export function setSessionCookie(res: Response, config: Config, sessionId: string): void {
  res.append(
    'Set-Cookie',
    serialize(SESSION_COOKIE, sessionId, baseOptions(config, 60 * 60 * 12, true)),
  );
}

export function clearSessionCookie(res: Response, config: Config): void {
  res.append(
    'Set-Cookie',
    serialize(SESSION_COOKIE, '', { ...baseOptions(config, 0, true), maxAge: 0 }),
  );
}

export function setOidcTransitCookie(res: Response, config: Config, value: string): void {
  res.append(
    'Set-Cookie',
    serialize(OIDC_TRANSIT_COOKIE, value, baseOptions(config, 60 * 10, true)),
  );
}

export function clearOidcTransitCookie(res: Response, config: Config): void {
  res.append(
    'Set-Cookie',
    serialize(OIDC_TRANSIT_COOKIE, '', { ...baseOptions(config, 0, true), maxAge: 0 }),
  );
}

/** Cookie CSRF: legível por JS de propósito (padrão double-submit). */
export function setCsrfCookie(res: Response, config: Config, token: string): void {
  res.append(
    'Set-Cookie',
    serialize(CSRF_COOKIE, token, baseOptions(config, 60 * 60 * 12, false)),
  );
}
