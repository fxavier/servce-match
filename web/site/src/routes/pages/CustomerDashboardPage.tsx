import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { PlusCircle } from 'lucide-react';
import { Seo } from '../../components/Seo';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonCard } from '../../components/ui/Skeleton';
import { formatDateTime } from '../../lib/dates';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';
import { useAuth } from '../../features/auth/useAuth';

const STATUS_LABEL: Record<string, string> = {
  DRAFT: 'Rascunho',
  PUBLISHED: 'Publicado',
  IN_NEGOTIATION: 'Em negociação',
  CONFIRMED: 'Confirmado',
  IN_PROGRESS: 'Em curso',
  COMPLETED: 'Concluído',
  CANCELLED: 'Cancelado',
};

export function CustomerDashboardPage() {
  const { user } = useAuth();
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['requests', 'mine'],
    queryFn: () => services.requests.listMine({ limit: 6 }),
  });

  const active = data?.items.filter((request) => !['COMPLETED', 'CANCELLED'].includes(request.status)) ?? [];

  return (
    <div className="mx-auto max-w-[1280px] px-5 py-10 sm:px-8 lg:px-10">
      <Seo title="O meu painel" description="Acompanhe os seus pedidos e propostas." />
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="eyebrow text-signal-500">O MEU PAINEL</p>
          <h1 className="mt-2 text-h2 font-display font-bold text-foreground">Olá, {user?.displayName?.split(' ')[0] ?? ''}</h1>
        </div>
        <Link
          to="/pedidos/novo"
          className="inline-flex h-11 items-center gap-1.5 rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-5 text-sm font-medium text-accent-fg"
        >
          <PlusCircle aria-hidden="true" className="size-4" strokeWidth={1.5} />
          Publicar novo pedido
        </Link>
      </div>

      <section className="mt-8">
        <h2 className="text-h2 font-display font-bold text-foreground">Pedidos ativos</h2>
        {error ? (
          <div className="mt-4">
            <ErrorState problem={toProblem(error)} onRetry={() => void refetch()} />
          </div>
        ) : isLoading ? (
          <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <SkeletonCard key={index} />
            ))}
          </div>
        ) : active.length === 0 ? (
          <div className="mt-4">
            <EmptyState
              title="Ainda sem pedidos ativos"
              description="Publique o seu primeiro pedido e comece a receber orçamentos."
              action={
                <Link to="/pedidos/novo" className="text-sm font-medium text-orange-600 hover:underline">
                  Publicar um pedido →
                </Link>
              }
            />
          </div>
        ) : (
          <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {active.map((request) => (
              <Link key={request.id} to={`/pedidos/${request.id}`} className="surface-card surface-card--interactive block p-5">
                <Badge tone="signal">{STATUS_LABEL[request.status]}</Badge>
                <p className="mt-3 text-card-title font-display font-semibold text-foreground">{request.title}</p>
                <p className="mt-1 text-caption text-muted">{request.category?.name}</p>
                <p className="mt-3 font-mono text-eyebrow tracking-[0.08em] text-muted">
                  {request.proposalCount ?? 0} PROPOSTA{(request.proposalCount ?? 0) === 1 ? '' : 'S'}
                </p>
                {request.publishedAt ? (
                  <p className="mt-1 text-caption text-muted">Publicado {formatDateTime(request.publishedAt)}</p>
                ) : null}
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
