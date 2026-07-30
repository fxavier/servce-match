/**
 * Estes endpoints (`/auth/me`, `/auth/login`, `/auth/register`,
 * `/auth/logout`) são do BFF, não do domínio — não vivem em
 * `docs/api/openapi.yaml` (contrato partilhado com o mobile, que continua
 * na RFC 8252 — ADR-0009/ADR-0012 D1) e por isso não saem do cliente
 * gerado. É código de infraestrutura de sessão, isolado aqui: todas as
 * outras chamadas HTTP passam por `services/http.ts`.
 *
 * O browser nunca vê `access_token`/`refresh_token`/password — só este
 * cookie de sessão opaco `HttpOnly` (CLAUDE.md §4, ADR-0012 D3). A password
 * introduzida no formulário viaja num único pedido para `/auth/login` ou
 * `/auth/register` e nunca é guardada em `localStorage`/`sessionStorage`
 * nem em estado React para além do formulário controlado.
 */
import { throwProblem } from '../../lib/problem';
import type { ProblemDetails } from '../../services/types';
import type { LoginCredentials, RegisterInput, Role, SessionUser } from './types';

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

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}

/**
 * `POST` para uma rota de sessão do BFF, com o cabeçalho CSRF
 * *double-submit* (mesmo padrão de `services/http.ts`). Lança
 * `ProblemDetails` em qualquer resposta não-2xx — nunca distingue no
 * cliente as várias razões possíveis, só repete o que o servidor decidiu
 * mostrar (ADR-0012 D7.3).
 */
async function postAuthAction(path: string, body?: unknown): Promise<void> {
  const csrfToken = readCookie('sm_csrf');
  const res = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(csrfToken ? { 'X-CSRF-Token': csrfToken } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.ok) return;

  const contentType = res.headers.get('content-type') ?? '';
  if (contentType.includes('application/problem+json') || contentType.includes('application/json')) {
    let problem: ProblemDetails | undefined;
    try {
      problem = (await res.json()) as ProblemDetails;
    } catch {
      // corpo não era JSON válido — cai para o problema genérico abaixo.
      problem = undefined;
    }
    if (problem) throwProblem(problem);
  }
  throwProblem({
    type: 'https://errors.servimatch.pt/unknown',
    title: 'Não foi possível completar o pedido.',
    status: res.status,
  });
}

/**
 * `POST /auth/login` (ADR-0012 D3): Direct Access Grant *server-to-server*
 * contra o Keycloak. A resposta nunca inclui tokens — só o `Set-Cookie` de
 * sessão. Lança em credenciais inválidas; chamar `fetchSession()` a seguir
 * confirma a sessão a partir da fonte de verdade (`GET /auth/me`).
 */
export async function login(credentials: LoginCredentials): Promise<void> {
  await postAuthAction('/auth/login', credentials);
}

/**
 * `POST /auth/register` (ADR-0012 D2): cria o utilizador no Keycloak via
 * Admin REST API e faz login imediato na mesma resposta. Lança em `409`
 * (email já registado — o BFF opta por essa divergência da
 * anti-enumeração estrita no registo, ver `web/bff/src/routes/auth.ts`) e
 * nos restantes erros. Numa resposta de sucesso, `fetchSession()` a seguir
 * confirma se o login automático pós-registo ficou mesmo autenticado.
 */
export async function register(input: RegisterInput): Promise<void> {
  await postAuthAction('/auth/register', input);
}

/** `POST /auth/logout` (ADR-0012 D3): destrói a sessão do BFF e revoga o refresh token. Devolve `204`, sem `logoutUrl`. */
export async function logout(): Promise<void> {
  await postAuthAction('/auth/logout');
}
