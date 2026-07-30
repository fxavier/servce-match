/**
 * Normalização do tempo de resposta de `POST /auth/login` (ADR-0012 D7.4).
 *
 * Um PISO de latência ("nunca responder antes de X ms") não chega: o
 * caminho "password errada" paga a derivação de hash do Keycloak e a
 * contabilidade de força bruta (`quickLoginCheckMilliSeconds`), e por vezes
 * ULTRAPASSA qualquer piso razoável — nesse caso o piso deixa de esconder
 * nada e a diferença volta a ser mensurável. A mitigação correta é um PRAZO
 * FIXO acima do p99 do caminho lento: se o trabalho terminou antes do prazo,
 * espera até lá; se terminou depois, arredonda para cima até ao próximo
 * múltiplo de `quantumMs` — nunca devolve o tempo exato que o Keycloak
 * demorou, só um de um conjunto discreto de valores.
 *
 * A válvula de escape (`maxDelayMs`) existe para uma degradação do IdP
 * (rede lenta, Keycloak em baixo) não segurar o pedido indefinidamente —
 * acima desse teto responde-se de imediato e regista-se telemetria (nunca
 * PII: só a duração e um identificador de correlação).
 */
export interface LoginTimingConfig {
  floorMs: number;
  quantumMs: number;
  maxDelayMs: number;
}

export interface LoginTimingTelemetry {
  onEscapeValve: (info: { elapsedMs: number; correlationId: string }) => void;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function quantizedTarget(elapsedMs: number, timing: LoginTimingConfig): number {
  if (elapsedMs <= timing.floorMs) return timing.floorMs;
  return Math.ceil(elapsedMs / timing.quantumMs) * timing.quantumMs;
}

/**
 * Executa `work` e devolve o resultado (ou relança o erro) só depois de o
 * tempo total decorrido atingir o alvo normalizado — nunca antes. `work`
 * nunca deve lançar de forma a saltar a normalização: por isso capturamos
 * sucesso e falha da mesma maneira antes de decidir quanto falta esperar.
 */
export async function withNormalizedTiming<T>(
  work: () => Promise<T>,
  timing: LoginTimingConfig,
  telemetry: LoginTimingTelemetry,
  correlationId: string,
): Promise<T> {
  const start = Date.now();
  const outcome = await settle(work);
  const elapsedMs = Date.now() - start;

  let targetMs = quantizedTarget(elapsedMs, timing);
  if (targetMs > timing.maxDelayMs) {
    telemetry.onEscapeValve({ elapsedMs, correlationId });
    targetMs = Math.max(elapsedMs, timing.maxDelayMs);
  }

  const remaining = targetMs - elapsedMs;
  if (remaining > 0) {
    await sleep(remaining);
  }

  if (outcome.ok) return outcome.value;
  throw outcome.error;
}

type Settled<T> = { ok: true; value: T } | { ok: false; error: unknown };

async function settle<T>(work: () => Promise<T>): Promise<Settled<T>> {
  try {
    return { ok: true, value: await work() };
  } catch (error) {
    return { ok: false, error };
  }
}
