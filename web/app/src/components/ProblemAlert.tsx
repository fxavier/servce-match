import { isSubscriptionRequired, type ProblemDetails } from '../lib/problem';

/**
 * Apresenta um erro RFC 9457. Ramifica-se pelo `type` (nunca por texto —
 * CLAUDE.md §5). O caso `subscription-required` é estado de produto, não
 * erro: convida a subscrever em vez de mostrar um alerta genérico
 * (CLAUDE.md §4 — o gating é decidido no servidor, a UI só espelha).
 */
export function ProblemAlert({ problem }: { problem: ProblemDetails }) {
  if (isSubscriptionRequired(problem)) {
    return (
      <div role="alert" className="problem-alert problem-alert--subscription">
        <p>{problem.detail ?? 'É preciso uma subscrição ativa para continuar.'}</p>
        <a href="/subscriptions">Ver planos de subscrição</a>
      </div>
    );
  }

  return (
    <div role="alert" className="problem-alert">
      <p className="problem-alert__title">{problem.title}</p>
      {problem.detail ? <p>{problem.detail}</p> : null}
      {problem.errors && problem.errors.length > 0 ? (
        <ul>
          {problem.errors.map((fieldError, index) => (
            <li key={`${fieldError.field ?? index}-${index}`}>
              {fieldError.field ? `${fieldError.field}: ` : ''}
              {fieldError.message}
            </li>
          ))}
        </ul>
      ) : null}
      {problem.instance ? (
        <p className="problem-alert__reference">
          Referência para suporte: <code>{problem.instance}</code>
        </p>
      ) : null}
    </div>
  );
}
