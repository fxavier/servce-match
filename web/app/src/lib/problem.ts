import type { components } from '../api/generated/schema';

export type ProblemDetails = components['schemas']['ProblemDetails'];

/**
 * Erros do backend vêm em RFC 9457 (`application/problem+json`) — ramifica-se
 * sempre pelo `type` (URI estável), nunca por comparação de texto em
 * `title`/`detail` (isso é para humanos e muda). Ver CLAUDE.md §5 e a skill
 * openapi-contract-first.
 */
export const PROBLEM_TYPE = {
  subscriptionRequired: 'https://errors.servimatch.pt/subscription-required',
} as const;

export function isProblemDetails(value: unknown): value is ProblemDetails {
  return (
    typeof value === 'object' &&
    value !== null &&
    'status' in value &&
    typeof (value as { status: unknown }).status === 'number' &&
    'title' in value &&
    typeof (value as { title: unknown }).title === 'string'
  );
}

export function isSubscriptionRequired(problem: ProblemDetails): boolean {
  return problem.type === PROBLEM_TYPE.subscriptionRequired;
}
