import { randomUUID } from 'node:crypto';
import { timingSafeEqual } from 'node:crypto';
import type { NextFunction, Request, Response } from 'express';
import { CSRF_COOKIE, readCookie, setCsrfCookie } from './cookies.js';
import { sendCsrfRejected } from './problemDetails.js';
import type { Config } from './config.js';

const CSRF_HEADER = 'x-csrf-token';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/**
 * Cookie de sessão implica CSRF (ADR-0002 nota de segurança). Padrão
 * double-submit: o BFF põe um token legível por JS num cookie; qualquer
 * escrita tem de repetir esse valor num header custom. Uma página de outra
 * origem consegue fazer o browser enviar o cookie automaticamente, mas não
 * consegue ler o valor para o pôr no header (isolamento de origem do
 * browser) nem consegue definir o header sem acionar CORS — que este BFF
 * nunca autoriza para outra origem.
 */
export function ensureCsrfCookie(config: Config) {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!readCookie(req, CSRF_COOKIE)) {
      setCsrfCookie(res, config, randomUUID());
    }
    next();
  };
}

export function verifyCsrf(req: Request, res: Response, next: NextFunction): void {
  if (SAFE_METHODS.has(req.method)) {
    next();
    return;
  }
  const cookieToken = readCookie(req, CSRF_COOKIE);
  const headerToken = req.header(CSRF_HEADER);
  if (!cookieToken || !headerToken || !constantTimeEqual(cookieToken, headerToken)) {
    sendCsrfRejected(res);
    return;
  }
  next();
}

function constantTimeEqual(a: string, b: string): boolean {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) return false;
  return timingSafeEqual(bufA, bufB);
}
