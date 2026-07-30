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
  /**
   * epoch ms em que a SESSÃO expira, sempre, independentemente de qualquer
   * renovação por refresh token. Fixado uma única vez em `create()` e nunca
   * tocado por `update()` — é o que impede uma sessão vazada de ficar viva
   * indefinidamente enquanto o refresh continuar a funcionar.
   */
  absoluteExpiresAt: number;
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
  /** sub -> id da sessão mais recente, para fixação de sessão (uma sessão ativa por utilizador). */
  private readonly latestSessionByUser = new Map<string, string>();
  /** Deduplicação de renovações concorrentes do mesmo id de sessão. */
  private readonly refreshLocks = new Map<string, Promise<SessionRecord | undefined>>();

  constructor(private readonly absoluteTtlSeconds = 60 * 60 * 12) {}

  /**
   * Cria uma sessão nova (id novo sempre — proteção contra fixação de
   * sessão) e destrói qualquer sessão anterior do mesmo utilizador
   * (`sub`), para que reautenticar-se invalide sessões esquecidas/vazadas
   * em vez de as deixar a coexistir indefinidamente.
   */
  create(tokens: {
    accessToken: string;
    refreshToken?: string;
    idToken?: string;
    expiresInSeconds: number;
  }): SessionRecord {
    const claims = decodeJwtPayload<KeycloakAccessTokenClaims>(tokens.accessToken);
    const now = Date.now();
    const record: SessionRecord = {
      id: randomUUID(),
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      idToken: tokens.idToken,
      accessTokenExpiresAt: now + tokens.expiresInSeconds * 1000,
      absoluteExpiresAt: now + this.absoluteTtlSeconds * 1000,
      user: {
        sub: claims.sub,
        email: claims.email,
        username: claims.preferred_username,
        roles: claims.realm_access?.roles ?? [],
      },
      createdAt: now,
    };

    const previousId = this.latestSessionByUser.get(claims.sub);
    if (previousId && previousId !== record.id) {
      this.sessions.delete(previousId);
    }
    this.latestSessionByUser.set(claims.sub, record.id);
    this.sessions.set(record.id, record);
    return record;
  }

  /** Devolve a sessão, expirando-a preguiçosamente se já passou o TTL absoluto. */
  get(sessionId: string): SessionRecord | undefined {
    const record = this.sessions.get(sessionId);
    if (!record) return undefined;
    if (Date.now() >= record.absoluteExpiresAt) {
      this.destroy(sessionId);
      return undefined;
    }
    return record;
  }

  /**
   * Aplica um patch a uma sessão existente. NUNCA atualiza
   * `absoluteExpiresAt` — a renovação do access/refresh token não estende o
   * TTL absoluto da sessão, de propósito (ADR-0012, ciclo de vida da sessão).
   */
  update(sessionId: string, patch: Partial<Omit<SessionRecord, 'absoluteExpiresAt' | 'id'>>): SessionRecord | undefined {
    const existing = this.sessions.get(sessionId);
    if (!existing) return undefined;
    const updated: SessionRecord = { ...existing, ...patch, id: existing.id, absoluteExpiresAt: existing.absoluteExpiresAt };
    this.sessions.set(sessionId, updated);
    return updated;
  }

  destroy(sessionId: string): void {
    const record = this.sessions.get(sessionId);
    this.sessions.delete(sessionId);
    if (record && this.latestSessionByUser.get(record.user.sub) === sessionId) {
      this.latestSessionByUser.delete(record.user.sub);
    }
  }

  /** Destrói a sessão ativa (se existir) de um utilizador, por `sub`. */
  destroyByUser(sub: string): void {
    const sessionId = this.latestSessionByUser.get(sub);
    if (sessionId) this.destroy(sessionId);
  }

  /** Considera "perto de expirar" com 30s de margem para evitar corridas. */
  isExpiringSoon(record: SessionRecord): boolean {
    return Date.now() > record.accessTokenExpiresAt - 30_000;
  }

  /**
   * Varrimento periódico das sessões cujo TTL absoluto já passou. `get()` já
   * expira preguiçosamente uma sessão consultada, mas uma sessão nunca mais
   * consultada (ex.: browser fechado) ficaria em memória para sempre sem
   * isto — chamado por um `setInterval` em `server.ts`, nunca pelos testes
   * via temporizador real (usam relógio controlado + chamada direta).
   */
  sweepExpired(now = Date.now()): number {
    let swept = 0;
    for (const [id, record] of this.sessions) {
      if (now >= record.absoluteExpiresAt) {
        this.destroy(id);
        swept += 1;
      }
    }
    return swept;
  }

  /**
   * Executa `refresher` no máximo uma vez concorrentemente por id de
   * sessão — duas chamadas simultâneas que despoletem renovação do mesmo
   * `session.id` (ex.: dois pedidos `/api/**` em paralelo com o access
   * token quase a expirar) partilham a mesma promessa em vez de cada uma
   * chamar `refreshTokenGrant` com o refresh token antigo, o que faria a
   * segunda falhar sempre que o Keycloak rodar o refresh token na resposta.
   */
  async refreshOnce(
    sessionId: string,
    refresher: (session: SessionRecord) => Promise<Partial<Omit<SessionRecord, 'absoluteExpiresAt' | 'id'>>>,
  ): Promise<SessionRecord | undefined> {
    const inFlight = this.refreshLocks.get(sessionId);
    if (inFlight) return inFlight;

    const session = this.get(sessionId);
    if (!session) return undefined;

    const promise = (async () => {
      try {
        const patch = await refresher(session);
        return this.update(sessionId, patch);
      } finally {
        this.refreshLocks.delete(sessionId);
      }
    })();

    this.refreshLocks.set(sessionId, promise);
    return promise;
  }
}
