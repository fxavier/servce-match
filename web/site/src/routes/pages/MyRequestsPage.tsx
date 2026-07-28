import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { Select } from '../../components/ui/Select';
import { SkeletonCard } from '../../components/ui/Skeleton';
import { formatDateTime } from '../../lib/dates';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';
import type { RequestStatus } from '../../services/types';

const STATUS_OPTIONS: { value: RequestStatus | ''; label: string }[] = [
  { value: '', label: 'Todos os estados' },
  { value: 'DRAFT', label: 'Rascunho' },
  { value: 'PUBLISHED', label: 'Publicado' },
  { value: 'IN_NEGOTIATION', label: 'Em negociação' },
  { value: 'CONFIRMED', label: 'Confirmado' },
  { value: 'IN_PROGRESS', label: 'Em curso' },
  { value: 'COMPLETED', label: 'Concluído' },
  { value: 'CANCELLED', label: 'Cancelado' },
];

export function MyRequestsPage() {
  const [status, setStatus] = useState<RequestStatus | ''>('');
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['requests', 'mine', status],
    queryFn: () => services.requests.listMine({ status: status || undefined, limit: 20 }),
  });

  return (
    <div className="mx-auto max-w-[1280px] px-5 py-10 sm:px-8 lg:px-10">
      <Seo title="Os meus pedidos" description="Lista dos seus pedidos de serviço." />
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-h2 font-display font-bold text-foreground">Os meus pedidos</h1>
        <Select aria-label="Filtrar por estado" value={status} onChange={(event) => setStatus(event.target.value as RequestStatus | '')} className="max-w-[200px]">
          {STATUS_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
      </div>

      {error ? (
        <div className="mt-6">
          <ErrorState problem={toProblem(error)} onRetry={() => void refetch()} />
        </div>
      ) : isLoading ? (
        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 4 }).map((_, index) => (
            <SkeletonCard key={index} />
          ))}
        </div>
      ) : !data || data.items.length === 0 ? (
        <div className="mt-6">
          <EmptyState title="Sem pedidos para este filtro" />
        </div>
      ) : (
        <ul className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.items.map((request) => (
            <li key={request.id}>
              <Link to={`/pedidos/${request.id}`} className="surface-card surface-card--interactive block h-full p-5">
                <Badge tone="signal">{request.status}</Badge>
                <p className="mt-3 text-card-title font-display font-semibold text-foreground">{request.title}</p>
                <p className="mt-1 text-caption text-muted">{request.category?.name}</p>
                <p className="mt-3 text-caption text-muted">Criado {formatDateTime(request.createdAt)}</p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
