import { randomUUID } from 'node:crypto';
import { decodeJwtPayload, type KeycloakAccessTokenClaims } from './jwt.js';

export interface SessionUser {
  sub: string;
  email?: string;
  username?: string;
  roles: string[];
}

export interface SessionRecord {
  id: string;
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  /** epoch ms em que o access_token expira. */
  accessTokenExpiresAt: number;
  user: SessionUser;
  createdAt: number;
}

/**
 * Store de sessão em memória do processo. Suficiente para uma única
 * instância de desenvolvimento/demo (é o que este ambiente local corre).
 *
 * Limitação conhecida a documentar para produção: com múltiplas instâncias
 * do BFF atrás de um load balancer isto precisa de passar para um store
 * partilhado (Redis, já presente na stack — ADR-0006) com sticky sessions
 * como alternativa só de curto prazo. Tokens nunca saem do processo do BFF
 * de qualquer forma — a mudança é só onde o *Map* vive.
 */
export class SessionStore {
  private readonly sessions = new Map<string, SessionRecord>();

  create(tokens: {
    accessToken: string;
    refreshToken?: string;
    idToken?: string;
    expiresInSeconds: number;
  }): SessionRecord {
    const claims = decodeJwtPayload<KeycloakAccessTokenClaims>(tokens.accessToken);
    const record: SessionRecord = {
      id: randomUUID(),
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      idToken: tokens.idToken,
      accessTokenExpiresAt: Date.now() + tokens.expiresInSeconds * 1000,
      user: {
        sub: claims.sub,
        email: claims.email,
        username: claims.preferred_username,
        roles: claims.realm_access?.roles ?? [],
      },
      createdAt: Date.now(),
    };
    this.sessions.set(record.id, record);
    return record;
  }

  get(sessionId: string): SessionRecord | undefined {
    return this.sessions.get(sessionId);
  }

  update(sessionId: string, patch: Partial<SessionRecord>): SessionRecord | undefined {
    const existing = this.sessions.get(sessionId);
    if (!existing) return undefined;
    const updated = { ...existing, ...patch };
    this.sessions.set(sessionId, updated);
    return updated;
  }

  destroy(sessionId: string): void {
    this.sessions.delete(sessionId);
  }

  /** Considera "perto de expirar" com 30s de margem para evitar corridas. */
  isExpiringSoon(record: SessionRecord): boolean {
    return Date.now() > record.accessTokenExpiresAt - 30_000;
  }
}
