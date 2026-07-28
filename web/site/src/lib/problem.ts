import type { ProblemDetails } from '../services/types';

export type { ProblemDetails };

/**
 * Erros do backend vêm em RFC 9457 (`application/problem+json`) — ramifica-se
 * sempre pelo `type` (URI estável), nunca por comparação de texto em
 * `title`/`detail` (isso é para humanos e muda). Ver CLAUDE.md §5 e a skill
 * openapi-contract-first.
 */
export const PROBLEM_TYPE = {
  subscriptionRequired: 'https://errors.servimatch.pt/subscription-required',
  validation: 'https://errors.servimatch.pt/validation',
  notFound: 'https://errors.servimatch.pt/not-found',
  conflict: 'https://errors.servimatch.pt/conflict',
  unauthorized: 'https://errors.servimatch.pt/unauthorized',
  unknown: 'https://errors.servimatch.pt/unknown',
} as const;

export function isProblemDetails(value: unknown): value is ProblemDetails {
  return (
    typeof value === 'object' &&
    value !== null &&
    'status' in value &&
    typeof value.status === 'number' &&
    'title' in value &&
    typeof value.title === 'string'
  );
}

export function isSubscriptionRequired(problem: ProblemDetails): boolean {
  return problem.type === PROBLEM_TYPE.subscriptionRequired;
}

export function isValidationProblem(problem: ProblemDetails): boolean {
  return problem.type === PROBLEM_TYPE.validation || problem.status === 422;
}

/** Normaliza qualquer erro (rede, parsing, RFC 9457) para um `ProblemDetails` apresentável. */
export function toProblem(error: unknown, fallbackTitle = 'Algo correu mal.'): ProblemDetails {
  if (isProblemDetails(error)) return error;
  return {
    type: PROBLEM_TYPE.unknown,
    title: fallbackTitle,
    status: 0,
    detail: 'Não foi possível ligar ao servidor. Verifica a tua ligação e tenta novamente.',
  };
}

/** Mapeia `errors[]` (campo/mensagem) de um 422 para o formato do react-hook-form `setError`. */
export function fieldErrorsFrom(problem: ProblemDetails): { field: string; message: string }[] {
  return (problem.errors ?? []).filter(
    (entry): entry is { field: string; message: string } =>
      typeof entry.field === 'string' && typeof entry.message === 'string',
  );
}

/**
 * Lança um `ProblemDetails` RFC 9457 verdadeiro — ponto único no repositório
 * que faz `throw` de algo que não é um `Error`, de propósito: o tipo de
 * erro de domínio desta app é sempre `ProblemDetails` (mock e HTTP real
 * partilham a mesma forma), nunca uma instância de `Error`.
 */
export function throwProblem(problem: ProblemDetails): never {
  // eslint-disable-next-line @typescript-eslint/only-throw-error -- ver comentário acima.
  throw problem;
}
