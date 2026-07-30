/**
 * Rate limiter em memória, janela fixa, por chave arbitrária (ADR-0012 D7.1).
 * Suficiente para uma única instância do BFF (mesma limitação documentada em
 * `session.ts` — múltiplas instâncias precisam de um store partilhado,
 * Redis/ADR-0006, fora de âmbito aqui).
 */
export class FixedWindowRateLimiter {
  private readonly buckets = new Map<string, { count: number; windowStart: number }>();

  constructor(
    private readonly windowMs: number,
    private readonly max: number,
  ) {}

  /** Regista uma tentativa para `key`. Não altera o contador se já estiver esgotado. */
  consume(key: string, now = Date.now()): { allowed: boolean; retryAfterMs: number } {
    const bucket = this.buckets.get(key);
    if (!bucket || now - bucket.windowStart >= this.windowMs) {
      this.buckets.set(key, { count: 1, windowStart: now });
      return { allowed: true, retryAfterMs: 0 };
    }
    if (bucket.count < this.max) {
      bucket.count += 1;
      return { allowed: true, retryAfterMs: 0 };
    }
    return { allowed: false, retryAfterMs: bucket.windowStart + this.windowMs - now };
  }

  /** Limpa janelas expiradas — higiene de memória, chamado periodicamente por `server.ts`. */
  sweep(now = Date.now()): number {
    let swept = 0;
    for (const [key, bucket] of this.buckets) {
      if (now - bucket.windowStart >= this.windowMs) {
        this.buckets.delete(key);
        swept += 1;
      }
    }
    return swept;
  }
}

/**
 * Limita por IP real E por email, nesta ordem, ANTES de contactar o
 * Keycloak (ADR-0012 D7.1). A ordem importa: um atacante que percorre
 * muitos emails a partir do mesmo IP esgota a SUA PRÓPRIA quota (por IP)
 * sem nunca chegar a consumir a quota (por email) de nenhuma vítima
 * individual — "consome a quota do atacante antes de gastar a da vítima".
 */
export class AuthAttemptLimiter {
  private readonly byIp: FixedWindowRateLimiter;
  private readonly byEmail: FixedWindowRateLimiter;

  constructor(windowMs: number, maxPerIp: number, maxPerEmail: number) {
    this.byIp = new FixedWindowRateLimiter(windowMs, maxPerIp);
    this.byEmail = new FixedWindowRateLimiter(windowMs, maxPerEmail);
  }

  check(ip: string, email: string, now = Date.now()): { allowed: boolean; retryAfterMs: number } {
    const ipResult = this.byIp.consume(`ip:${ip}`, now);
    if (!ipResult.allowed) return ipResult;

    const emailResult = this.byEmail.consume(`email:${email.trim().toLowerCase()}`, now);
    if (!emailResult.allowed) return emailResult;

    return { allowed: true, retryAfterMs: 0 };
  }

  sweep(now = Date.now()): void {
    this.byIp.sweep(now);
    this.byEmail.sweep(now);
  }
}
