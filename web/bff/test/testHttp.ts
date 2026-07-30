import type { Express } from 'express';
import request from 'supertest';

/** Extrai um cookie (par `name=value`, sem atributos) de uma resposta supertest. */
export function pickCookie(res: request.Response, name: string): string | undefined {
  const raw = res.headers['set-cookie'] as unknown as string[] | undefined;
  const found = raw?.find((c) => c.startsWith(`${name}=`));
  return found?.split(';')[0];
}

/**
 * Obtém um cookie CSRF válido fazendo um pedido `GET` inofensivo primeiro
 * (`ensureCsrfCookie` corre em todos os pedidos — ver `src/csrf.ts`) — é o
 * que qualquer cliente real faz antes do primeiro `POST` (ex.: a SPA chama
 * `GET /auth/me` ao arrancar).
 */
export async function getCsrf(app: Express): Promise<{ cookie: string; token: string }> {
  const res = await request(app).get('/auth/me');
  const cookie = pickCookie(res, 'sm_csrf');
  if (!cookie) throw new Error('sm_csrf cookie not set by GET /auth/me');
  return { cookie, token: cookie.split('=')[1] };
}
