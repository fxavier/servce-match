import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { components } from '../api/generated/schema';
import { EmptyState } from '../components/EmptyState';
import { LoadingState } from '../components/LoadingState';
import { ProblemAlert } from '../components/ProblemAlert';
import { formatDateTime } from '../lib/dates';
import { formatMoney } from '../lib/money';
import { isProblemDetails, type ProblemDetails } from '../lib/problem';

type ServiceRequest = components['schemas']['ServiceRequest'];
type Proposal = components['schemas']['Proposal'];

function toProblem(error: unknown): ProblemDetails {
  if (isProblemDetails(error)) return error;
  return {
    type: 'https://errors.servimatch.pt/unknown',
    title: 'Não foi possível completar o pedido.',
    status: 0,
  };
}

export function RequestDetailPage() {
  const { requestId } = useParams<{ requestId: string }>();

  const [request, setRequest] = useState<ServiceRequest | undefined>(undefined);
  const [requestProblem, setRequestProblem] = useState<ProblemDetails | undefined>(undefined);

  const [proposals, setProposals] = useState<Proposal[] | undefined>(undefined);
  const [nextCursor, setNextCursor] = useState<string | null | undefined>(undefined);
  const [proposalsProblem, setProposalsProblem] = useState<ProblemDetails | undefined>(undefined);
  const [loadingMore, setLoadingMore] = useState(false);

  const [acceptingId, setAcceptingId] = useState<string | undefined>(undefined);
  const [acceptProblem, setAcceptProblem] = useState<ProblemDetails | undefined>(undefined);

  const loadProposals = useCallback(
    async (cursor?: string) => {
      if (!requestId) return;
      const { data, error } = await api.GET('/v1/requests/{requestId}/proposals', {
        params: { path: { requestId }, query: cursor ? { cursor } : undefined },
      });
      if (error) {
        setProposalsProblem(toProblem(error));
        return;
      }
      setProposals((previous) => (cursor ? [...(previous ?? []), ...data.items] : data.items));
      setNextCursor(data.page.nextCursor);
    },
    [requestId],
  );

  useEffect(() => {
    if (!requestId) return;
    let cancelled = false;

    api
      .GET('/v1/requests/{requestId}', { params: { path: { requestId } } })
      .then(({ data, error }) => {
        if (cancelled) return;
        if (error) {
          setRequestProblem(toProblem(error));
          return;
        }
        setRequest(data);
      })
      .catch(() => {
        if (!cancelled) setRequestProblem(toProblem(undefined));
      });

    loadProposals().catch(() => {
      if (!cancelled) setProposalsProblem(toProblem(undefined));
    });

    return () => {
      cancelled = true;
    };
  }, [requestId, loadProposals]);

  async function handleLoadMore() {
    if (!nextCursor) return;
    setLoadingMore(true);
    try {
      await loadProposals(nextCursor);
    } finally {
      setLoadingMore(false);
    }
  }

  async function handleAccept(proposalId: string) {
    setAcceptProblem(undefined);
    setAcceptingId(proposalId);
    try {
      const { error } = await api.POST('/v1/proposals/{proposalId}/accept', {
        params: { path: { proposalId } },
      });
      if (error) {
        setAcceptProblem(toProblem(error));
        return;
      }
      // O servidor decide o novo estado de todas as propostas (a aceite e as
      // restantes que passam a SUPERSEDED) — a UI não infere isso localmente,
      // volta a pedir a lista.
      await loadProposals();
    } finally {
      setAcceptingId(undefined);
    }
  }

  if (!requestId) {
    return <ProblemAlert problem={toProblem(undefined)} />;
  }

  return (
    <main>
      {requestProblem ? (
        <ProblemAlert problem={requestProblem} />
      ) : request ? (
        <header>
          <h1>{request.title}</h1>
          <p>Estado: {request.status}</p>
          <p>{request.proposalCount ?? 0} proposta(s) recebidas</p>
        </header>
      ) : (
        <LoadingState label="A carregar pedido…" />
      )}

      <section aria-labelledby="proposals-heading">
        <h2 id="proposals-heading">Propostas</h2>

        {proposalsProblem ? <ProblemAlert problem={proposalsProblem} /> : null}
        {acceptProblem ? <ProblemAlert problem={acceptProblem} /> : null}

        {proposals === undefined && !proposalsProblem ? (
          <LoadingState label="A carregar propostas…" />
        ) : proposals && proposals.length === 0 ? (
          <EmptyState>Ainda não há propostas para este pedido.</EmptyState>
        ) : (
          <ul>
            {proposals?.map((proposal) => (
              <li key={proposal.id}>
                <p>{proposal.providerSummary?.displayName ?? 'Prestador'}</p>
                <p>{formatMoney(proposal.price)}</p>
                {proposal.description ? <p>{proposal.description}</p> : null}
                {proposal.leadTimeDays !== undefined ? (
                  <p>Prazo estimado: {proposal.leadTimeDays} dia(s)</p>
                ) : null}
                <p>Recebida {formatDateTime(proposal.createdAt)}</p>
                <p>Estado: {proposal.status}</p>
                {proposal.status === 'SENT' ? (
                  <button
                    type="button"
                    onClick={() => handleAccept(proposal.id)}
                    disabled={acceptingId === proposal.id}
                  >
                    {acceptingId === proposal.id ? 'A aceitar…' : 'Aceitar proposta'}
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}

        {nextCursor ? (
          <button type="button" onClick={handleLoadMore} disabled={loadingMore}>
            {loadingMore ? 'A carregar…' : 'Carregar mais'}
          </button>
        ) : null}
      </section>
    </main>
  );
}
