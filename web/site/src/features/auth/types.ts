export type Role = 'CUSTOMER' | 'PROVIDER' | 'ADMIN';

/** Papéis que um utilizador pode escolher no registo — nunca `ADMIN` (ADR-0012 D2: allowlist no servidor, não confiança no cliente). */
export type RegisterableRole = 'CUSTOMER' | 'PROVIDER';

export interface SessionUser {
  id: string;
  displayName: string;
  email: string;
  roles: Role[];
  avatarUrl?: string | null;
}

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface RegisterInput {
  name: string;
  email: string;
  password: string;
  role: RegisterableRole;
}

export interface AuthContextValue {
  status: AuthStatus;
  user: SessionUser | undefined;
  /**
   * `POST /auth/login` no BFF (ADR-0012 D3) — nunca um redirect para o
   * Keycloak. Rejeita com `ProblemDetails` em credenciais inválidas; o BFF
   * devolve deliberadamente a mesma resposta para "email não existe" e
   * "password errada" (D7.3) — este cliente nunca tenta distinguir os dois.
   */
  login: (credentials: LoginCredentials) => Promise<SessionUser>;
  /**
   * `POST /auth/register` no BFF (ADR-0012 D2, D7.3) — cria a conta no
   * Keycloak (Admin REST API) e faz login imediato na mesma resposta.
   * Email novo e email já registado devolvem a MESMA resposta (`201`) —
   * nunca `409` só por o email já existir (defeito C3.1,
   * `docs/ESTADO-DO-SISTEMA.md`, já corrigido). Devolve `undefined` sem
   * lançar sempre que o login automático subsequente não confirmou sessão
   * — quer a conta tenha acabado de ser criada quer já existisse; este
   * cliente não tem como distinguir os dois casos, de propósito.
   */
  register: (input: RegisterInput) => Promise<SessionUser | undefined>;
  logout: () => Promise<void>;
  hasRole: (role: Role) => boolean;
}
