/**
 * Estes três endpoints (`/auth/me`, `/auth/login`, `/auth/logout`) são do
 * BFF, não do domínio — não vivem em `docs/api/openapi.yaml` (contrato
 * partilhado com o mobile, que não usa BFF — ADR-0009) e por isso não saem
 * do cliente gerado. É código de infraestrutura de sessão, isolado aqui:
 * todas as outras chamadas HTTP passam por `services/http.ts`.
 *
 * O browser nunca vê `access_token`/`refresh_token` — só este cookie de
 * sessão opaco `HttpOnly` (CLAUDE.md §4, ADR-0002).
 */
import type { Role, SessionUser } from './types';

interface BffSessionUser {
  sub: string;
  email?: string;
  username?: string;
  roles: string[];
}

type SessionResponse = { authenticated: true; user: BffSessionUser } | { authenticated: false };

function toSessionUser(user: BffSessionUser): SessionUser {
  const roles = user.roles.filter((role): role is Role => role === 'CUSTOMER' || role === 'PROVIDER' || role === 'ADMIN');
  return {
    id: user.sub,
    displayName: user.username ?? user.email ?? 'Utilizador',
    email: user.email ?? '',
    roles,
  };
}

export async function fetchSession(): Promise<SessionUser | undefined> {
  const res = await fetch('/auth/me', { credentials: 'include' });
  if (!res.ok) return undefined;
  const body = (await res.json()) as SessionResponse;
  return body.authenticated ? toSessionUser(body.user) : undefined;
}

/** Navegação de topo (não `fetch`): tem de ser um redirect real para o Keycloak via BFF. */
export function beginLogin(returnTo: string): void {
  window.location.href = `/auth/login?returnTo=${encodeURIComponent(returnTo)}`;
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}

export async function endSession(): Promise<string> {
  const csrfToken = readCookie('sm_csrf');
  const res = await fetch('/auth/logout', {
    method: 'POST',
    credentials: 'include',
    headers: csrfToken ? { 'X-CSRF-Token': csrfToken } : undefined,
  });
  if (!res.ok) {
    throw new Error('Falha ao terminar sessão no BFF.');
  }
  const body = (await res.json()) as { logoutUrl: string };
  return body.logoutUrl;
}
