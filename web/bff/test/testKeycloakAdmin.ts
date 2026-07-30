/**
 * Duplo de teste da Admin REST API do Keycloak (ADR-0012 D2), para
 * `POST /auth/register`. `keycloakAdmin.ts` fala com ela por `fetch` puro
 * (não pelo `customFetch` do `openid-client`, esse é o `testOidc.ts`) — por
 * isso este duplo intercepta o `fetch` GLOBAL (`vi.stubGlobal('fetch', ...)`,
 * como já faz `test/app.test.ts` para o proxy `/api`).
 *
 * Reproduz deliberadamente as duas armadilhas documentadas em
 * `infra/README.md`: `GET /roles` não existe aqui (só
 * `.../role-mappings/realm/available`), e a atribuição de role exige a
 * representação completa (`{id, name}`), nunca só `{name}`.
 */
export interface KeycloakAdminMockControls {
  /** Força a próxima chamada (qualquer endpoint) a devolver 401 — simula um token expirado/revogado a meio. */
  forceNext401(): void;
  /** Força o próximo `POST /users` a devolver este status (com corpo genérico do Keycloak). */
  forceCreateUserStatus(status: number): void;
  /** Força a próxima atribuição de role a devolver este status. */
  forceAssignRoleStatus(status: number): void;
  /** Faz `DELETE /users/{id}` falhar sempre (para testar o "órfão sem rollback"). */
  forceDeleteFailure(): void;
}

export interface KeycloakAdminMock {
  fetchMock: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;
  usersByEmail: Map<string, string>;
  roleAssignments: Map<string, string>;
  deletedUserIds: string[];
  controls: KeycloakAdminMockControls;
}

export function createKeycloakAdminMock(adminApiBaseUrl: string): KeycloakAdminMock {
  const usersByEmail = new Map<string, string>();
  const emailById = new Map<string, string>();
  const roleAssignments = new Map<string, string>();
  const deletedUserIds: string[] = [];
  let nextId = 1;

  let force401Once = false;
  let forcedCreateStatus: number | undefined;
  let forcedAssignStatus: number | undefined;
  let forceDeleteFailureFlag = false;

  const availableRoles = [
    { id: 'role-id-customer', name: 'CUSTOMER' },
    { id: 'role-id-provider', name: 'PROVIDER' },
  ];

  const basePath = new URL(adminApiBaseUrl).pathname;

  async function fetchMock(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    const request = new Request(input, init);
    const url = new URL(request.url);

    if (!request.url.startsWith(adminApiBaseUrl)) {
      throw new Error(`Unhandled fetch in keycloak admin mock: ${request.url}`);
    }

    if (force401Once) {
      force401Once = false;
      return new Response(null, { status: 401 });
    }

    const path = url.pathname.slice(basePath.length);

    if (request.method === 'POST' && path === '/users') {
      if (forcedCreateStatus !== undefined) {
        const status = forcedCreateStatus;
        forcedCreateStatus = undefined;
        return Response.json({ errorMessage: 'forced test failure' }, { status });
      }
      const body = (await request.json()) as { email: string };
      if (usersByEmail.has(body.email)) {
        return Response.json({ errorMessage: 'User exists with same username' }, { status: 409 });
      }
      const id = `kc-user-${nextId++}`;
      usersByEmail.set(body.email, id);
      emailById.set(id, body.email);
      return new Response(null, { status: 201, headers: { Location: `${adminApiBaseUrl}/users/${id}` } });
    }

    const availableMatch = path.match(/^\/users\/([^/]+)\/role-mappings\/realm\/available$/);
    if (request.method === 'GET' && availableMatch) {
      return Response.json(availableRoles);
    }

    const assignMatch = path.match(/^\/users\/([^/]+)\/role-mappings\/realm$/);
    if (request.method === 'POST' && assignMatch) {
      if (forcedAssignStatus !== undefined) {
        const status = forcedAssignStatus;
        forcedAssignStatus = undefined;
        return new Response(null, { status });
      }
      const roles = (await request.json()) as Array<{ id?: string; name?: string }>;
      if (!roles[0]?.id) {
        // Reproduz a armadilha real: representação incompleta -> 404.
        return Response.json({ error: 'Role not found' }, { status: 404 });
      }
      roleAssignments.set(assignMatch[1], roles[0].name ?? '');
      return new Response(null, { status: 204 });
    }

    const deleteMatch = path.match(/^\/users\/([^/]+)$/);
    if (request.method === 'DELETE' && deleteMatch) {
      if (forceDeleteFailureFlag) {
        return new Response(null, { status: 500 });
      }
      const id = deleteMatch[1];
      const email = emailById.get(id);
      if (email) usersByEmail.delete(email);
      emailById.delete(id);
      deletedUserIds.push(id);
      return new Response(null, { status: 204 });
    }

    throw new Error(`Unhandled admin fetch: ${request.method} ${path}`);
  }

  return {
    fetchMock,
    usersByEmail,
    roleAssignments,
    deletedUserIds,
    controls: {
      forceNext401: () => {
        force401Once = true;
      },
      forceCreateUserStatus: (status: number) => {
        forcedCreateStatus = status;
      },
      forceAssignRoleStatus: (status: number) => {
        forcedAssignStatus = status;
      },
      forceDeleteFailure: () => {
        forceDeleteFailureFlag = true;
      },
    },
  };
}
