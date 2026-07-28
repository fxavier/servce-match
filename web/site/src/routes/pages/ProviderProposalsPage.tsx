import { useQuery } from '@tanstack/react-query';
import { Seo } from '../../components/Seo';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonText } from '../../components/ui/Skeleton';
import { formatDateTime } from '../../lib/dates';
import { formatMoney } from '../../lib/money';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';

const STATUS_LABEL: Record<string, string> = {
  SENT: 'Enviada',
  ACCEPTED: 'Aceite',
  REJECTED: 'Rejeitada',
  EXPIRED: 'Expirada',
  SUPERSEDED: 'Substituída',
  CANCELLED: 'Cancelada',
};

const STATUS_TONE: Record<string, 'signal' | 'success' | 'neutral' | 'accent'> = {
  SENT: 'signal',
  ACCEPTED: 'success',
  REJECTED: 'neutral',
  EXPIRED: 'neutral',
  SUPERSEDED: 'neutral',
  CANCELLED: 'accent',
};

export function ProviderProposalsPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['proposals', 'mine'],
    queryFn: () => services.proposals.listMine({ limit: 40 }),
  });

  return (
    <div>
      <Seo title="As minhas propostas" description="Propostas enviadas e o respetivo estado." />
      <h1 className="text-h2 font-display font-bold text-foreground">As minhas propostas</h1>

      {error ? (
        <div className="mt-6">
          <ErrorState problem={toProblem(error)} onRetry={() => void refetch()} />
        </div>
      ) : isLoading ? (
        <div className="mt-6">
          <SkeletonText lines={5} />
        </div>
      ) : !data || data.items.length === 0 ? (
        <div className="mt-6">
          <EmptyState title="Ainda sem propostas enviadas" description="As propostas que enviar aparecem aqui com o respetivo estado." />
        </div>
      ) : (
        <ul className="mt-6 flex flex-col divide-y divide-line border-y border-line">
          {data.items.map((proposal) => (
            <li key={proposal.id} className="flex flex-col gap-2 py-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm font-medium text-foreground">Pedido #{proposal.requestId}</p>
                <p className="text-caption text-muted">Enviada {formatDateTime(proposal.createdAt)}</p>
              </div>
              <div className="flex items-center gap-3">
                <p className="font-display text-lg font-bold tabular-nums text-foreground">{formatMoney(proposal.price)}</p>
                <Badge tone={STATUS_TONE[proposal.status]}>{STATUS_LABEL[proposal.status]}</Badge>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
