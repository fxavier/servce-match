import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { SubscriptionUpsell } from '../../features/subscriptions/SubscriptionUpsell';
import { formatRelativeTime } from '../../lib/dates';
import { isSubscriptionRequired, toProblem } from '../../lib/problem';
import { services } from '../../services';

const URGENCY_TONE: Record<string, 'neutral' | 'accent'> = { LOW: 'neutral', NORMAL: 'neutral', HIGH: 'accent', URGENT: 'accent' };

function PlaceholderInboxCards() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: 6 }).map((_, index) => (
        <div key={index} className="surface-card p-5">
          <div className="h-4 w-2/3 rounded bg-surface-2" />
          <div className="mt-3 h-3 w-1/3 rounded bg-surface-2" />
          <div className="mt-4 h-3 w-full rounded bg-surface-2" />
        </div>
      ))}
    </div>
  );
}

export function ProviderInboxPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['provider-inbox'],
    queryFn: () => services.requests.listProviderInbox({ limit: 20 }),
  });

  const problem = error ? toProblem(error) : undefined;
  const blocked = Boolean(problem && isSubscriptionRequired(problem));

  return (
    <div>
      <Seo title="Pedidos elegíveis" description="Pedidos elegíveis para si, por categoria e zona." />
      <h1 className="text-h2 font-display font-bold text-foreground">Pedidos elegíveis</h1>
      <p className="mt-1 text-body text-muted">Pedidos publicados que correspondem às suas categorias e zonas de atuação.</p>

      <div className="mt-6">
        {blocked ? (
          <SubscriptionUpsell blocked>
            <PlaceholderInboxCards />
          </SubscriptionUpsell>
        ) : problem ? (
          <ErrorState problem={problem} onRetry={() => void refetch()} />
        ) : isLoading ? (
          <PlaceholderInboxCards />
        ) : !data || data.items.length === 0 ? (
          <EmptyState title="Sem pedidos elegíveis de momento" description="Assim que houver um pedido novo na sua categoria e zona, aparece aqui." />
        ) : (
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.items.map((request) => (
              <li key={request.id}>
                <Link to={`/pro/pedidos/${request.id}`} className="surface-card surface-card--interactive block h-full p-5">
                  <div className="flex items-center justify-between gap-2">
                    <Badge tone="neutral">{request.category?.name}</Badge>
                    {request.urgency ? <Badge tone={URGENCY_TONE[request.urgency]}>{request.urgency}</Badge> : null}
                  </div>
                  <p className="mt-3 text-card-title font-display font-semibold text-foreground">{request.title}</p>
                  <p className="mt-1 text-caption text-muted">{request.address?.city}</p>
                  <p className="mt-3 font-mono text-eyebrow tracking-[0.08em] text-muted">
                    {request.publishedAt ? formatRelativeTime(request.publishedAt).toUpperCase() : ''} · {request.proposalCount ?? 0} PROPOSTA(S)
                  </p>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
