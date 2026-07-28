import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { Dialog } from '../../components/ui/Dialog';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonText } from '../../components/ui/Skeleton';
import { RequestStatusStepper } from '../../features/requests/RequestStatusStepper';
import { ProposalsComparisonList } from '../../features/proposals/ProposalsComparisonList';
import { formatMoney } from '../../lib/money';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';
import type { Proposal } from '../../services/types';

export function RequestDetailPage() {
  const { requestId } = useParams<{ requestId: string }>();
  const queryClient = useQueryClient();
  const [confirmProposalId, setConfirmProposalId] = useState<string | undefined>(undefined);

  const requestQuery = useQuery({
    queryKey: ['requests', 'detail', requestId],
    queryFn: () => services.requests.get(requestId as string),
    enabled: Boolean(requestId),
  });

  const proposalsQuery = useQuery({
    queryKey: ['proposals', 'by-request', requestId],
    queryFn: () => services.proposals.listForRequest(requestId as string),
    enabled: Boolean(requestId),
  });

  const acceptMutation = useMutation({
    mutationFn: (proposalId: string) => services.proposals.accept(proposalId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['proposals', 'by-request', requestId] });
      void queryClient.invalidateQueries({ queryKey: ['requests', 'detail', requestId] });
      setConfirmProposalId(undefined);
    },
  });

  if (!requestId) return null;

  if (requestQuery.isLoading) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-16 sm:px-8">
        <SkeletonText lines={6} />
      </div>
    );
  }

  if (requestQuery.error || !requestQuery.data) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-16 sm:px-8">
        <ErrorState problem={toProblem(requestQuery.error)} onRetry={() => void requestQuery.refetch()} />
      </div>
    );
  }

  const request = requestQuery.data;
  const proposals = proposalsQuery.data?.items ?? [];

  return (
    <div className="mx-auto max-w-4xl px-5 py-[clamp(2.5rem,5vw,4rem)] sm:px-8">
      <Seo title={request.title} description={request.description ?? request.title} />

      <p className="font-mono text-eyebrow tracking-[0.08em] text-muted">PEDIDO · SM-2026-{request.id.replace(/[^0-9]/g, '').slice(-4).padStart(4, '0')}</p>
      <h1 className="mt-2 text-h1 font-display font-bold text-foreground">{request.title}</h1>
      {request.description ? <p className="mt-3 text-body text-muted">{request.description}</p> : null}

      <div className="mt-6 flex flex-wrap items-center gap-4">
        <RequestStatusStepper status={request.status} />
      </div>

      <dl className="mt-6 flex flex-wrap gap-x-8 gap-y-2 text-caption text-muted">
        <div>
          <dt className="inline">Categoria: </dt>
          <dd className="inline font-medium text-foreground">{request.category?.name ?? '—'}</dd>
        </div>
        <div>
          <dt className="inline">Morada: </dt>
          <dd className="inline font-medium text-foreground">
            {request.address?.city ?? '—'}
            {request.address?.postalCode ? `, ${request.address.postalCode}` : ''}
          </dd>
        </div>
        <div>
          <dt className="inline">Propostas recebidas: </dt>
          <dd className="inline font-medium text-foreground">{request.proposalCount ?? proposals.length}</dd>
        </div>
      </dl>

      <section className="mt-10" aria-labelledby="proposals-heading">
        <h2 id="proposals-heading" className="text-h2 font-display font-bold text-foreground">
          Orçamentos
        </h2>

        {proposalsQuery.error ? (
          <div className="mt-4">
            <ErrorState problem={toProblem(proposalsQuery.error)} onRetry={() => void proposalsQuery.refetch()} />
          </div>
        ) : proposalsQuery.isLoading ? (
          <div className="mt-4">
            <SkeletonText lines={4} />
          </div>
        ) : proposals.length === 0 ? (
          <div className="mt-4">
            <EmptyState title="Ainda sem propostas" description="Assim que um prestador enviar um orçamento, aparece aqui." />
          </div>
        ) : (
          <ProposalsComparisonList
            proposals={proposals}
            onAccept={(proposalId) => setConfirmProposalId(proposalId)}
            acceptingId={acceptMutation.isPending ? confirmProposalId : undefined}
          />
        )}

        {acceptMutation.isError ? (
          <div className="mt-4">
            <ErrorState problem={toProblem(acceptMutation.error)} />
          </div>
        ) : null}
      </section>

      <Dialog
        open={Boolean(confirmProposalId)}
        onClose={() => setConfirmProposalId(undefined)}
        title="Aceitar este orçamento?"
        description="As restantes propostas para este pedido passam automaticamente a 'Substituídas'. Esta ação não pode ser desfeita."
      >
        {confirmProposalId ? (
          <ConfirmAcceptBody
            proposalId={confirmProposalId}
            proposals={proposals}
            onCancel={() => setConfirmProposalId(undefined)}
            onConfirm={() => acceptMutation.mutate(confirmProposalId)}
            pending={acceptMutation.isPending}
          />
        ) : null}
      </Dialog>
    </div>
  );
}

function ConfirmAcceptBody({
  proposalId,
  proposals,
  onCancel,
  onConfirm,
  pending,
}: {
  proposalId: string;
  proposals: Proposal[];
  onCancel: () => void;
  onConfirm: () => void;
  pending: boolean;
}) {
  const proposal = proposals.find((item) => item.id === proposalId);

  return (
    <div>
      {proposal ? (
        <p className="text-body text-foreground">
          Vai aceitar o orçamento de <strong>{proposal.providerSummary?.displayName ?? 'este prestador'}</strong> no valor de{' '}
          <strong>{formatMoney(proposal.price)}</strong>.
        </p>
      ) : null}
      <div className="mt-5 flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="inline-flex h-10 items-center rounded-full border border-line px-4 text-sm font-medium text-foreground">
          Cancelar
        </button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={pending}
          className="inline-flex h-10 items-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-4 text-sm font-medium text-accent-fg disabled:opacity-60"
        >
          {pending ? 'A confirmar…' : 'Confirmar aceitação'}
        </button>
      </div>
    </div>
  );
}
