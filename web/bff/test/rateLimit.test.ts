import { describe, expect, it } from 'vitest';
import { AuthAttemptLimiter, FixedWindowRateLimiter } from '../src/rateLimit.js';

describe('FixedWindowRateLimiter', () => {
  it('permite até ao máximo dentro da janela e bloqueia a seguir', () => {
    const limiter = new FixedWindowRateLimiter(1_000, 3);
    const now = 0;
    expect(limiter.consume('k', now).allowed).toBe(true);
    expect(limiter.consume('k', now).allowed).toBe(true);
    expect(limiter.consume('k', now).allowed).toBe(true);
    const fourth = limiter.consume('k', now);
    expect(fourth.allowed).toBe(false);
    expect(fourth.retryAfterMs).toBeGreaterThan(0);
  });

  it('reinicia a janela depois de `windowMs`', () => {
    const limiter = new FixedWindowRateLimiter(1_000, 1);
    expect(limiter.consume('k', 0).allowed).toBe(true);
    expect(limiter.consume('k', 500).allowed).toBe(false);
    expect(limiter.consume('k', 1_001).allowed).toBe(true);
  });

  it('chaves diferentes têm orçamentos independentes', () => {
    const limiter = new FixedWindowRateLimiter(1_000, 1);
    expect(limiter.consume('a', 0).allowed).toBe(true);
    expect(limiter.consume('b', 0).allowed).toBe(true);
  });

  it('sweep remove janelas expiradas', () => {
    const limiter = new FixedWindowRateLimiter(1_000, 1);
    limiter.consume('k', 0);
    expect(limiter.sweep(500)).toBe(0);
    expect(limiter.sweep(2_000)).toBe(1);
  });
});

describe('AuthAttemptLimiter (ADR-0012 D7.1 — por IP e por email, IP primeiro)', () => {
  it('bloqueia por IP antes de gastar a quota de qualquer email individual', () => {
    const limiter = new AuthAttemptLimiter(1_000, 1, 100);
    expect(limiter.check('1.2.3.4', 'vitima1@example.pt', 0).allowed).toBe(true);
    // Mesmo IP, emails diferentes (varrimento de credential stuffing) — bloqueado pelo IP.
    const blocked = limiter.check('1.2.3.4', 'vitima2@example.pt', 0);
    expect(blocked.allowed).toBe(false);
  });

  it('bloqueia por email mesmo vindo de IPs diferentes (ataque distribuído contra uma vítima)', () => {
    const limiter = new AuthAttemptLimiter(1_000, 100, 1);
    expect(limiter.check('1.1.1.1', 'vitima@example.pt', 0).allowed).toBe(true);
    const blocked = limiter.check('2.2.2.2', 'vitima@example.pt', 0);
    expect(blocked.allowed).toBe(false);
  });

  it('normaliza o email (case/whitespace) para o mesmo balde', () => {
    const limiter = new AuthAttemptLimiter(1_000, 100, 1);
    expect(limiter.check('1.1.1.1', ' Vitima@Example.PT ', 0).allowed).toBe(true);
    const blocked = limiter.check('1.1.1.1', 'vitima@example.pt', 0);
    expect(blocked.allowed).toBe(false);
  });
});
