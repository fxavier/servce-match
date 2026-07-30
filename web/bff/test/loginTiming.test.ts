import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { withNormalizedTiming, type LoginTimingConfig } from '../src/loginTiming.js';

const TIMING: LoginTimingConfig = { floorMs: 400, quantumMs: 100, maxDelayMs: 3000 };

async function runWithWorkDelay(workDelayMs: number, shouldThrow: boolean): Promise<number> {
  const telemetry = { onEscapeValve: vi.fn() };
  const start = Date.now();
  const promise = withNormalizedTiming(
    async () => {
      await new Promise<void>((resolve) => setTimeout(resolve, workDelayMs));
      if (shouldThrow) throw new Error('simulated-auth-failure');
    },
    TIMING,
    telemetry,
    'corr-test',
  ).catch(() => undefined);

  // Corre exatamente até não haver mais temporizadores pendentes — avança o
  // relógio virtual só até onde os `setTimeout` internos precisam, não um
  // valor arbitrário a mais (que inflacionaria `Date.now()` além do real).
  await vi.runAllTimersAsync();
  await promise;
  return Date.now() - start;
}

describe('withNormalizedTiming (ADR-0012 D7.4 — normalização de tempo do login)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('duas latências reais diferentes (conta inexistente ~0ms, password errada ~150ms), ambas abaixo do piso, resultam em EXATAMENTE o mesmo tempo de resposta', async () => {
    // Este é o duplo do Keycloak "a sério": o caminho da conta inexistente
    // não paga derivação de hash (quase instantâneo); o da password errada
    // paga-a (aqui simulado com um atraso real). Um teste que só verificasse
    // "ambos >= floorMs" passaria mesmo que os dois tempos fossem
    // diferentes — por isso comparamos os dois tempos EXATOS entre si.
    const nonexistentAccountElapsed = await runWithWorkDelay(0, true);
    const wrongPasswordElapsed = await runWithWorkDelay(150, true);

    expect(nonexistentAccountElapsed).toBe(TIMING.floorMs);
    expect(wrongPasswordElapsed).toBe(TIMING.floorMs);
    expect(nonexistentAccountElapsed).toBe(wrongPasswordElapsed);
  });

  it('quando o caminho lento ultrapassa o piso, quantiza para o mesmo balde discreto em vez de vazar o tempo exato do IdP', async () => {
    // 550ms e 580ms são duas durações REAIS diferentes do lado do Keycloak,
    // ambas acima do piso (400ms) — um piso simples (`Math.max(elapsed,
    // floor)`) devolveria 550 e 580, dois valores distintos e mensuráveis.
    // A quantização arredonda ambos para o mesmo múltiplo de 100ms.
    const elapsedA = await runWithWorkDelay(550, true);
    const elapsedB = await runWithWorkDelay(580, true);

    expect(elapsedA).toBe(600);
    expect(elapsedB).toBe(600);
    expect(elapsedA).toBe(elapsedB);
  });

  it('sucesso e falha são normalizados da mesma forma (o resultado só se conhece pelo status HTTP, não pelo tempo)', async () => {
    const successElapsed = await runWithWorkDelay(50, false);
    const failureElapsed = await runWithWorkDelay(50, true);
    expect(successElapsed).toBe(failureElapsed);
  });

  it('válvula de escape: acima de maxDelayMs responde sem esperar mais e regista telemetria', async () => {
    const telemetry = { onEscapeValve: vi.fn() };
    const start = Date.now();
    const promise = withNormalizedTiming(
      async () => {
        await new Promise<void>((resolve) => setTimeout(resolve, 5_000));
      },
      TIMING,
      telemetry,
      'corr-escape',
    );
    await vi.runAllTimersAsync();
    await promise;
    const elapsed = Date.now() - start;

    expect(telemetry.onEscapeValve).toHaveBeenCalledTimes(1);
    expect(telemetry.onEscapeValve).toHaveBeenCalledWith(
      expect.objectContaining({ correlationId: 'corr-escape' }),
    );
    // Não espera mais além do que o próprio trabalho já demorou — não há
    // válvula de escape que "prenda" o pedido ainda mais.
    expect(elapsed).toBe(5_000);
  });

  it('propaga o valor de sucesso do trabalho depois de normalizado', async () => {
    const telemetry = { onEscapeValve: vi.fn() };
    const promise = withNormalizedTiming(async () => 'ok-value', TIMING, telemetry, 'corr-value');
    await vi.runAllTimersAsync();
    await expect(promise).resolves.toBe('ok-value');
  });
});
