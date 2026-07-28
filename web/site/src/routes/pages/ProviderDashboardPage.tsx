import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { Badge } from '../../components/ui/Badge';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonText } from '../../components/ui/Skeleton';
import { CountUp } from '../../components/motion/CountUp';
import { formatMoney } from '../../lib/money';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';

const STATUS_LABEL: Record<string, string> = {
  PENDING: 'Pendente',
  ACTIVE: 'Ativa',
  PAST_DUE: 'Pagamento em atraso',
  EXPIRED: 'Expirada',
  CANCELLED: 'Cancelada',
};

const STATUS_TONE: Record<string, 'success' | 'accent' | 'neutral'> = {
  PENDING: 'neutral',
  ACTIVE: 'success',
  PAST_DUE: 'accent',
  EXPIRED: 'accent',
  CANCELLED: 'neutral',
};

export function ProviderDashboardPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['provider-dashboard'],
    queryFn: () => services.providerDashboard.stats(),
  });

  return (
    <div>
      <Seo title="Painel do prestador" description="Estado da subscrição, pedidos elegíveis e propostas enviadas." />
      <h1 className="text-h2 font-display font-bold text-foreground">Painel</h1>

      {error ? (
        <div className="mt-6">
          <ErrorState problem={toProblem(error)} onRetry={() => void refetch()} />
        </div>
      ) : isLoading || !data ? (
        <div className="mt-6">
          <SkeletonText lines={5} />
        </div>
      ) : (
        <div className="mt-6 flex flex-col gap-6">
          <div className="surface-card flex flex-wrap items-center justify-between gap-4 p-5">
            <div>
              <p className="text-caption text-muted">Estado da subscrição</p>
              <Badge tone={STATUS_TONE[data.subscriptionStatus]} className="mt-1.5">
                {STATUS_LABEL[data.subscriptionStatus]}
              </Badge>
            </div>
            <Link to="/pro/subscricao" className="text-sm font-medium text-orange-600 hover:underline">
              Gerir subscrição →
            </Link>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="Pedidos elegíveis novos" value={data.newEligibleRequests} />
            <StatCard label="Propostas enviadas" value={data.proposalsByStatus.SENT} />
            <StatCard label="Taxa de aceitação" value={data.acceptanceRate} suffix="%" />
            <div className="surface-card p-5">
              <p className="text-caption text-muted">Receita estimada</p>
              <p className="mt-2 font-display text-3xl font-extrabold tabular-nums text-foreground">
                {formatMoney(data.estimatedRevenue)}
              </p>
            </div>
          </div>

          <div className="surface-card p-5">
            <p className="text-caption text-muted">Atividade dos últimos 30 dias</p>
            <div className="mt-4 flex h-24 items-end gap-1">
              {data.last30Days.map((day) => (
                <div
                  key={day.date}
                  className="flex-1 rounded-t bg-orange-500/70"
                  style={{ height: `${8 + (day.proposalsSent + day.proposalsAccepted) * 30}%` }}
                  title={day.date}
                />
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function StatCard({ label, value, suffix = '' }: { label: string; value: number; suffix?: string }) {
  return (
    <div className="surface-card p-5">
      <p className="text-caption text-muted">{label}</p>
      <p className="mt-2 font-display text-3xl font-extrabold tabular-nums text-foreground">
        <CountUp value={value} suffix={suffix} />
      </p>
    </div>
  );
}
