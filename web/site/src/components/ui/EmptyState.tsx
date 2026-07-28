import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import { Inbox } from 'lucide-react';

export interface EmptyStateProps {
  icon?: LucideIcon;
  title: string;
  description?: string;
  action?: ReactNode;
}

/** Vazio: ilustração + explicação + ação (§10) — nunca uma lista simplesmente ausente. */
export function EmptyState({ icon: Icon = Inbox, title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-line px-6 py-14 text-center">
      <div className="rounded-full bg-surface-2 p-3">
        <Icon aria-hidden="true" className="size-6 text-muted" strokeWidth={1.5} />
      </div>
      <p className="text-card-title font-display font-semibold text-foreground">{title}</p>
      {description ? <p className="max-w-sm text-body text-muted">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}
