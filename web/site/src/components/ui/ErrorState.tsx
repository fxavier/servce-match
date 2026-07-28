import { AlertTriangle } from 'lucide-react';
import type { ProblemDetails } from '../../lib/problem';
import { Button } from './Button';

export interface ErrorStateProps {
  problem: ProblemDetails;
  onRetry?: () => void;
}

/**
 * Erro derivado do `type` do Problem Details, com `correlationId` em Plex
 * Mono para referência de suporte (§5.2, §10) — nunca um alerta genérico.
 */
export function ErrorState({ problem, onRetry }: ErrorStateProps) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-lg border border-orange-500/30 bg-orange-500/5 px-6 py-10 text-center">
      <div className="rounded-full bg-orange-500/12 p-3">
        <AlertTriangle aria-hidden="true" className="size-6 text-orange-600" strokeWidth={1.5} />
      </div>
      <p className="text-card-title font-display font-semibold text-foreground">{problem.title}</p>
      {problem.detail ? <p className="max-w-sm text-body text-muted">{problem.detail}</p> : null}
      {onRetry ? (
        <Button type="button" variant="outline" size="sm" onClick={onRetry} className="mt-1">
          Tentar novamente
        </Button>
      ) : null}
      {problem.correlationId ? (
        <p className="font-mono text-eyebrow tracking-[0.08em] text-muted">REF {problem.correlationId}</p>
      ) : null}
    </div>
  );
}
