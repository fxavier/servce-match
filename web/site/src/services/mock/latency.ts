/**
 * Latência artificial (300–800 ms) + probabilidade de erro configurável
 * (§8.3). Em testes (`import.meta.env.MODE === 'test'`) a latência colapsa
 * para 0 — não há razão para os testes esperarem 800 ms por chamada.
 */
const IS_TEST = import.meta.env.MODE === 'test';

export async function withLatency<T>(fn: () => T | Promise<T>, msRange: [number, number] = [300, 800]): Promise<T> {
  if (!IS_TEST) {
    const [min, max] = msRange;
    const delay = min + Math.random() * (max - min);
    await new Promise((resolve) => setTimeout(resolve, delay));
  }
  return fn();
}

/** Painel de mocks (dev-only) pode ligar isto para forçar cenários de erro a propósito. */
export const mockFaults = {
  forceSubscriptionRequired: false,
  forceValidationError: false,
  forceConflict: false,
};
