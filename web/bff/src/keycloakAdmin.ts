import * as client from 'openid-client';
import type { Config } from './config.js';

/** Roles de domínio atribuíveis a partir do registo (ADR-0012 D2). `ADMIN`
 * NUNCA entra aqui — reencaminhar o campo `role` do pedido tal como chega
 * seria escalada de privilégio direta. */
const ALLOWED_REALM_ROLES = new Set(['CUSTOMER', 'PROVIDER']);

export function isAllowedRealmRole(role: unknown): role is 'CUSTOMER' | 'PROVIDER' {
  return typeof role === 'string' && ALLOWED_REALM_ROLES.has(role);
}

export interface RealmRoleRepresentation {
  id: string;
  name: string;
  [key: string]: unknown;
}

export class KeycloakAdminError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly cause?: unknown,
  ) {
    super(message);
    this.name = 'KeycloakAdminError';
  }
}

export type CreateUserResult = { outcome: 'created'; id: string } | { outcome: 'conflict' };

/**
 * Cliente fino da Admin REST API do Keycloak, autenticado pelo *service
 * account* do client `servimatch-bff` (ADR-0012 D2). O token de
 * `client_credentials` é cacheado em memória do processo e só se pede um
 * novo:
 * - quando o cache expira (com margem), ou
 * - quando um pedido devolve 401 (o token pode ter sido revogado/expirado
 *   entretanto) — nesse caso pede-se um token novo e repete-se UMA vez, não
 *   se pede um token novo por cada registo.
 */
export class KeycloakAdminClient {
  private cachedToken: { value: string; expiresAt: number } | undefined;

  constructor(
    private readonly config: Config,
    private readonly oidcConfig: client.Configuration,
  ) {}

  private async serviceAccountToken(forceRefresh = false): Promise<string> {
    if (!forceRefresh && this.cachedToken && Date.now() < this.cachedToken.expiresAt) {
      return this.cachedToken.value;
    }
    const tokens = await client.clientCredentialsGrant(this.oidcConfig);
    const expiresInSeconds = tokens.expiresIn() ?? 60;
    this.cachedToken = {
      value: tokens.access_token,
      // Margem de 5s para nunca usar um token que expira a meio do pedido seguinte.
      expiresAt: Date.now() + Math.max(0, expiresInSeconds - 5) * 1000,
    };
    return this.cachedToken.value;
  }

  private async adminFetch(path: string, init: RequestInit, allowRetryOn401 = true): Promise<Response> {
    const token = await this.serviceAccountToken();
    const response = await fetch(`${this.config.keycloak.adminApiBaseUrl}${path}`, {
      ...init,
      headers: {
        ...(init.headers ?? {}),
        Authorization: `Bearer ${token}`,
      },
    });
    if (response.status === 401 && allowRetryOn401) {
      await this.serviceAccountToken(true);
      return this.adminFetch(path, init, false);
    }
    return response;
  }

  /**
   * `POST /admin/realms/servimatch/users`. `emailVerified:false` sempre —
   * afirmar `true` seria mentir num claim que terceiros confiam (ADR-0012
   * D5). `requiredActions` fica sempre vazio: com o realm em
   * `verifyEmail:false` isso já é o comportamento por omissão, mas fica
   * explícito porque é o invariante de que depende o login imediato a
   * seguir (uma required action pendente faz o Direct Access Grant seguinte
   * devolver `invalid_grant` opaco).
   */
  async createUser(input: {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
  }): Promise<CreateUserResult> {
    const response = await this.adminFetch('/users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: input.email,
        email: input.email,
        enabled: true,
        emailVerified: false,
        requiredActions: [],
        firstName: input.firstName,
        lastName: input.lastName,
        credentials: [{ type: 'password', value: input.password, temporary: false }],
      }),
    });

    if (response.status === 409) {
      return { outcome: 'conflict' };
    }
    if (!response.ok) {
      const body = await safeReadText(response);
      throw new KeycloakAdminError('keycloak-admin-create-user-failed', response.status, body);
    }

    const location = response.headers.get('location');
    const id = location?.split('/').filter(Boolean).pop();
    if (!id) {
      throw new KeycloakAdminError('keycloak-admin-create-user-missing-location', response.status);
    }
    return { outcome: 'created', id };
  }

  /**
   * O service account só tem `manage-users`+`view-users`, não gestão geral
   * de roles: `GET /roles` dá 403. O caminho que funciona com este âmbito
   * mínimo é `GET /users/{id}/role-mappings/realm/available`
   * (`infra/README.md`), e o `POST` seguinte exige a representação completa
   * da role (pelo menos `id`+`name`) devolvida por essa chamada — um array
   * com só `{name}` dá 404 "Role not found".
   */
  async assignRealmRole(userId: string, roleName: 'CUSTOMER' | 'PROVIDER'): Promise<void> {
    const availableResponse = await this.adminFetch(
      `/users/${encodeURIComponent(userId)}/role-mappings/realm/available`,
      { method: 'GET' },
    );
    if (!availableResponse.ok) {
      throw new KeycloakAdminError(
        'keycloak-admin-list-available-roles-failed',
        availableResponse.status,
        await safeReadText(availableResponse),
      );
    }
    const available = (await availableResponse.json()) as RealmRoleRepresentation[];
    const role = available.find((candidate) => candidate.name === roleName);
    if (!role) {
      throw new KeycloakAdminError('keycloak-admin-role-not-found-in-realm', 500);
    }

    const assignResponse = await this.adminFetch(`/users/${encodeURIComponent(userId)}/role-mappings/realm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify([role]),
    });
    if (!assignResponse.ok) {
      throw new KeycloakAdminError(
        'keycloak-admin-assign-role-failed',
        assignResponse.status,
        await safeReadText(assignResponse),
      );
    }
  }

  /** Rollback do registo (ADR-0012 D2): uma conta sem role é pior que inexistente. */
  async deleteUser(userId: string): Promise<boolean> {
    const response = await this.adminFetch(`/users/${encodeURIComponent(userId)}`, { method: 'DELETE' });
    return response.ok;
  }
}

async function safeReadText(response: Response): Promise<string | undefined> {
  try {
    return await response.text();
  } catch {
    return undefined;
  }
}
