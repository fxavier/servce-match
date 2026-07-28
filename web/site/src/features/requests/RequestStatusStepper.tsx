import { Check } from 'lucide-react';
import { cn } from '../../lib/cn';
import type { RequestStatus } from '../../services/types';

const STEPS: { status: RequestStatus; label: string }[] = [
  { status: 'DRAFT', label: 'Rascunho' },
  { status: 'PUBLISHED', label: 'Publicado' },
  { status: 'IN_NEGOTIATION', label: 'Em negociação' },
  { status: 'CONFIRMED', label: 'Confirmado' },
  { status: 'IN_PROGRESS', label: 'Em curso' },
  { status: 'COMPLETED', label: 'Concluído' },
];

export function RequestStatusStepper({ status }: { status: RequestStatus }) {
  if (status === 'CANCELLED') {
    return (
      <div role="status" className="inline-flex items-center gap-2 rounded-full border border-orange-500/30 bg-orange-500/8 px-3 py-1.5 text-sm font-medium text-orange-600">
        Pedido cancelado
      </div>
    );
  }

  const currentIndex = STEPS.findIndex((step) => step.status === status);

  return (
    <ol role="status" aria-label="Estado do pedido" className="flex items-center gap-1 overflow-x-auto">
      {STEPS.map((step, index) => {
        const done = index < currentIndex;
        const active = index === currentIndex;
        return (
          <li key={step.status} className="flex items-center gap-1">
            <div className="flex flex-col items-center gap-1.5">
              <span
                className={cn(
                  'flex size-6 shrink-0 items-center justify-center rounded-full border text-xs font-medium',
                  done && 'border-orange-500 bg-orange-500 text-accent-fg',
                  active && 'border-orange-500 text-orange-600',
                  !done && !active && 'border-line text-muted',
                )}
              >
                {done ? <Check aria-hidden="true" className="size-3.5" strokeWidth={2.5} /> : index + 1}
              </span>
              <span className={cn('whitespace-nowrap text-caption', active ? 'font-medium text-foreground' : 'text-muted')}>
                {step.label}
              </span>
            </div>
            {index < STEPS.length - 1 ? (
              <span className={cn('h-px w-8 shrink-0', done ? 'bg-orange-500' : 'bg-line')} aria-hidden="true" />
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}
