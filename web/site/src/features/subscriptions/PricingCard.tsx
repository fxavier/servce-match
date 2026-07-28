import { Check } from 'lucide-react';
import { Link } from 'react-router-dom';
import { formatMoney } from '../../lib/money';
import { cn } from '../../lib/cn';
import type { SubscriptionPlan } from '../../services/types';
import { PLAN_HIGHLIGHTS } from '../../constants/planHighlights';

export interface PricingCardProps {
  plan: SubscriptionPlan;
  highlighted?: boolean;
}

export function PricingCard({ plan, highlighted = false }: PricingCardProps) {
  const highlights = PLAN_HIGHLIGHTS[plan.code] ?? [];

  return (
    <div
      className={cn(
        'surface-card flex flex-col gap-6 p-6',
        highlighted && 'glow-accent border-orange-500 ring-1 ring-orange-500',
      )}
    >
      {highlighted ? (
        <span className="w-fit rounded-full bg-orange-500 px-3 py-1 text-xs font-medium text-accent-fg">
          Mais escolhido
        </span>
      ) : null}
      <div>
        <p className="text-card-title font-display font-semibold text-foreground">{plan.name}</p>
        <p className="mt-2 flex items-baseline gap-1">
          <span className="font-display text-4xl font-extrabold tabular-nums text-foreground">
            {formatMoney(plan.price)}
          </span>
          <span className="text-caption text-muted">/mês</span>
        </p>
      </div>
      <ul className="flex flex-1 flex-col gap-2.5">
        {highlights.map((item) => (
          <li key={item} className="flex items-start gap-2 text-body text-muted">
            <Check aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-orange-500" strokeWidth={2} />
            {item}
          </li>
        ))}
      </ul>
      <Link
        to={`/entrar?returnTo=${encodeURIComponent('/pro/subscricao')}`}
        className={cn(
          'inline-flex h-11 items-center justify-center rounded-full text-sm font-medium transition-colors',
          highlighted
            ? 'bg-gradient-to-r from-orange-600 to-orange-400 text-accent-fg hover:brightness-110'
            : 'border border-line text-foreground hover:border-orange-500/40',
        )}
      >
        Começar como prestador
      </Link>
    </div>
  );
}
