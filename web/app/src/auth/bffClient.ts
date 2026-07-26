/**
 * Estes três endpoints (`/auth/me`, `/auth/login`, `/auth/logout`) são do
 * BFF, não do domínio — não vivem em `docs/api/openapi.yaml` (contrato
 * partilhado com o mobile, que não usa BFF — ADR-0009) e por isso não saem
 * do cliente gerado. É código de infraestrutura de sessão, isolado aqui e
 * não espalhado pela app: todas as outras chamadas HTTP passam por
 * `src/api/client.ts`.
 */

export interface SessionUser {
  sub: string;
  email?: string;
  username?: string;
  roles: string[];
}

export type SessionResponse = { authenticated: true; user: SessionUser } | { authenticated: false };

export async function fetchSession(): Promise<SessionResponse> {
  const res = await fetch('/auth/me', { credentials: 'include' });
  if (!res.ok) {
    return { authenticated: false };
  }
  return (await res.json()) as SessionResponse;
}

/** Navegação de topo (não `fetch`): tem de ser um redirect real para o Keycloak. */
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
