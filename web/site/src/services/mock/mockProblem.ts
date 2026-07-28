import { throwProblem as throwProblemBase } from '../../lib/problem';
import type { ProblemDetails } from '../types';

/** Lança um `ProblemDetails` RFC 9457 verdadeiro a partir de um serviço mock (§8.7). */
export function throwProblem(problem: Omit<ProblemDetails, 'correlationId'> & { correlationId?: string }): never {
  const withCorrelation: ProblemDetails = {
    correlationId: problem.correlationId ?? crypto.randomUUID(),
    ...problem,
  };
  throwProblemBase(withCorrelation);
}
