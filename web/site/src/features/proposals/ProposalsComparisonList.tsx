import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Avatar } from '../../components/ui/Avatar';
import { Badge } from '../../components/ui/Badge';
import { RatingStars } from '../../components/ui/RatingStars';
import { Select } from '../../components/ui/Select';
import { formatCountdown, formatDateTime } from '../../lib/dates';
import { formatMoney } from '../../lib/money';
import { cn } from '../../lib/cn';
import type { Proposal } from '../../services/types';

type SortKey = 'price' | 'leadTime' | 'rating';

const STATUS_TONE: Record<string, 'neutral' | 'accent' | 'success' | 'signal'> = {
  SENT: 'signal',
  ACCEPTED: 'success',
  REJECTED: 'neutral',
  CANCELLED: 'neutral',
  EXPIRED: 'neutral',
  SUPERSEDED: 'neutral',
};

const STATUS_LABEL: Record<string, string> = {
  SENT: 'Enviada',
  ACCEPTED: 'Aceite',
  REJECTED: 'Rejeitada',
  CANCELLED: 'Cancelada',
  EXPIRED: 'Expirada',
  SUPERSEDED: 'Substituída',
};

export interface ProposalsComparisonListProps {
  proposals: Proposal[];
  onAccept: (proposalId: string) => void;
  acceptingId?: string;
}

export function ProposalsComparisonList({ proposals, onAccept, acceptingId }: ProposalsComparisonListProps) {
  const [sortKey, setSortKey] = useState<SortKey>('price');
  const [expandedId, setExpandedId] = useState<string | undefined>(undefined);

  const sorted = useMemo(() => {
    const copy = [...proposals];
    copy.sort((a, b) => {
      if (sortKey === 'price') return a.price.amountCents - b.price.amountCents;
      if (sortKey === 'leadTime') return (a.leadTimeDays ?? 999) - (b.leadTimeDays ?? 999);
      return (b.providerSummary?.ratingAvg ?? 0) - (a.providerSummary?.ratingAvg ?? 0);
    });
    return copy;
  }, [proposals, sortKey]);

  return (
    <div>
      <div className="flex items-center justify-end gap-2">
        <label htmlFor="proposals-sort" className="text-caption text-muted">
          Ordenar por
        </label>
        <Select id="proposals-sort" value={sortKey} onChange={(event) => setSortKey(event.target.value as SortKey)} className="max-w-[180px]">
          <option value="price">Preço</option>
          <option value="leadTime">Prazo</option>
          <option value="rating">Avaliação</option>
        </Select>
      </div>

      <ul className="mt-4 flex flex-col gap-4">
        {sorted.map((proposal) => {
          const isExpanded = expandedId === proposal.id;
          return (
            <li key={proposal.id} className="surface-card p-5">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex items-start gap-3">
                  <Avatar name={proposal.providerSummary?.displayName ?? 'Prestador'} imageUrl={proposal.providerSummary?.avatarUrl} />
                  <div>
                    <p className="text-card-title font-display font-semibold text-foreground">
                      {proposal.providerSummary?.displayName ?? 'Prestador'}
                    </p>
                    {proposal.providerSummary ? (
                      <RatingStars value={proposal.providerSummary.ratingAvg} count={proposal.providerSummary.ratingCount} className="mt-1" />
                    ) : null}
                    <div className="mt-1.5 flex flex-wrap gap-1.5">
                      {proposal.providerSummary?.verified ? <Badge tone="signal">Verificado</Badge> : null}
                      {proposal.providerSummary?.premiumBadge ? <Badge tone="accent">Premium</Badge> : null}
                      <Badge tone={STATUS_TONE[proposal.status]}>{STATUS_LABEL[proposal.status]}</Badge>
                    </div>
                  </div>
                </div>

                <div className="flex flex-col items-start gap-1 sm:items-end">
                  <p className="font-display text-2xl font-bold tabular-nums text-foreground">{formatMoney(proposal.price)}</p>
                  {proposal.leadTimeDays !== undefined ? (
                    <p className="text-caption text-muted">Prazo: {proposal.leadTimeDays} dia(s)</p>
                  ) : null}
                  {proposal.validUntil && proposal.status === 'SENT' ? (
                    <p className="font-mono text-eyebrow tracking-[0.08em] text-orange-600">
                      {formatCountdown(proposal.validUntil).toUpperCase()}
                    </p>
                  ) : null}
                </div>
              </div>

              {proposal.description ? (
                <div className="mt-3">
                  <button
                    type="button"
                    onClick={() => setExpandedId(isExpanded ? undefined : proposal.id)}
                    className="text-caption font-medium text-orange-600 hover:underline"
                    aria-expanded={isExpanded}
                  >
                    {isExpanded ? 'Ocultar descrição' : 'Ver descrição'}
                  </button>
                  {isExpanded ? <p className="mt-2 text-body text-muted">{proposal.description}</p> : null}
                </div>
              ) : null}

              <div className="mt-4 flex flex-wrap items-center gap-3">
                <p className="font-mono text-eyebrow tracking-[0.08em] text-muted">
                  RECEBIDA {formatDateTime(proposal.createdAt).toUpperCase()}
                </p>
                {proposal.status === 'SENT' ? (
                  <div className={cn('ml-auto flex gap-2')}>
                    <button
                      type="button"
                      onClick={() => onAccept(proposal.id)}
                      disabled={acceptingId === proposal.id}
                      className="inline-flex h-10 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-4 text-sm font-medium text-accent-fg disabled:opacity-60"
                    >
                      {acceptingId === proposal.id ? 'A aceitar…' : 'Aceitar'}
                    </button>
                    <Link
                      to="/conversas"
                      className="inline-flex h-10 items-center justify-center rounded-full border border-line px-4 text-sm font-medium text-foreground hover:border-orange-500/40"
                    >
                      Conversar
                    </Link>
                  </div>
                ) : null}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
