import type { Response } from 'express';

/**
 * RFC 9457 Problem Details, no mesmo formato que o backend usa
 * (docs/api/openapi.yaml → ProblemDetails). O BFF só emite estes erros para
 * falhas que lhe pertencem (sessão, CSRF, OIDC) — erros de domínio vêm do
 * backend e são reencaminhados tal e qual em problemDetailsProxy.
 */
export interface ProblemDetailsBody {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  [extensionMember: string]: unknown;
}

const PROBLEM_BASE = 'https://errors.servimatch.pt/';

export function sendProblem(
  res: Response,
  status: number,
  slug: string,
  title: string,
  detail?: string,
  extra?: Record<string, unknown>,
): void {
  const body: ProblemDetailsBody = {
    type: `${PROBLEM_BASE}${slug}`,
    title,
    status,
    ...(detail ? { detail } : {}),
    ...(extra ?? {}),
  };
  res.status(status).type('application/problem+json').send(JSON.stringify(body));
}

export const sendUnauthenticated = (res: Response, detail?: string): void =>
  sendProblem(res, 401, 'unauthenticated', 'Sessão inexistente ou expirada.', detail);

export const sendCsrfRejected = (res: Response): void =>
  sendProblem(res, 403, 'csrf-rejected', 'Pedido rejeitado por proteção CSRF.');

export const sendBadGateway = (res: Response, detail?: string): void =>
  sendProblem(res, 502, 'upstream-unavailable', 'O backend não respondeu.', detail);
