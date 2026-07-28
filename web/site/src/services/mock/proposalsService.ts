import type { ProposalsService } from '../interfaces';
import type { Proposal } from '../types';
import { providerById } from './fixtures/providers';
import { mockCurrentUser } from './currentUser';
import { mockDb } from './db';
import { withLatency } from './latency';
import { throwProblem } from './mockProblem';

function paginate(items: Proposal[], limit = 20, cursor?: string) {
  const offset = cursor ? Number(cursor) : 0;
  const page = items.slice(offset, offset + limit);
  const nextOffset = offset + limit;
  return { items: page, page: { nextCursor: nextOffset < items.length ? String(nextOffset) : null } };
}

export const proposalsServiceMock: ProposalsService = {
  listForRequest(requestId, params) {
    return withLatency(() => paginate(mockDb.proposals.filter((p) => p.requestId === requestId), params?.limit, params?.cursor));
  },
  listMine(params) {
    return withLatency(() => {
      const user = mockCurrentUser.get();
      const items = mockDb.proposals.filter((p) => p.providerId === (user?.id ?? ''));
      return paginate(items, params?.limit, params?.cursor);
    });
  },
  create(requestId, body) {
    return withLatency(() => {
      if (!mockDb.providerSubscriptionActive) {
        throwProblem({
          type: 'https://errors.servimatch.pt/subscription-required',
          title: 'É preciso uma subscrição ativa para enviar orçamentos.',
          status: 403,
          detail: 'A tua subscrição não está ativa — escolhe um plano para poderes responder a este pedido.',
        });
      }
      const request = mockDb.requests.find((r) => r.id === requestId);
      if (!request) throwProblem({ type: 'https://errors.servimatch.pt/not-found', title: 'Pedido não encontrado.', status: 404 });
      if (request.status !== 'PUBLISHED') {
        throwProblem({
          type: 'https://errors.servimatch.pt/conflict',
          title: 'Este pedido já não aceita novas propostas.',
          status: 409,
          detail: `O pedido está em estado "${request.status}".`,
        });
      }
      if (!body.description || body.description.trim().length === 0) {
        throwProblem({
          type: 'https://errors.servimatch.pt/validation',
          title: 'Dados inválidos.',
          status: 422,
          errors: [{ field: 'description', message: 'A descrição do orçamento é obrigatória.' }],
        });
      }
      const user = mockCurrentUser.get();
      const provider = user ? providerById(user.id) : undefined;
      const proposal: Proposal = {
        id: `pr-${crypto.randomUUID().slice(0, 8)}`,
        requestId,
        providerId: user?.id ?? 'p-0001',
        providerSummary: provider
          ? {
              id: provider.id,
              displayName: provider.displayName,
              headline: provider.headline,
              companyName: provider.companyName,
              ratingAvg: provider.ratingAvg,
              ratingCount: provider.ratingCount,
              verified: provider.verified,
              premiumBadge: provider.premiumBadge,
              avatarUrl: null,
            }
          : undefined,
        price: body.price,
        description: body.description,
        leadTimeDays: body.leadTimeDays,
        validUntil: body.validUntil ?? null,
        status: 'SENT',
        createdAt: new Date().toISOString(),
      };
      mockDb.addProposal(proposal);
      mockDb.updateRequest(requestId, { proposalCount: (request.proposalCount ?? 0) + 1 });
      return proposal;
    });
  },
  accept(proposalId) {
    return withLatency(() => {
      const proposal = mockDb.proposals.find((p) => p.id === proposalId);
      if (!proposal) throwProblem({ type: 'https://errors.servimatch.pt/not-found', title: 'Proposta não encontrada.', status: 404 });
      if (proposal.status !== 'SENT') {
        throwProblem({
          type: 'https://errors.servimatch.pt/conflict',
          title: 'Esta proposta já não pode ser aceite.',
          status: 409,
          detail: `A proposta está em estado "${proposal.status}".`,
        });
      }
      const accepted = mockDb.updateProposal(proposalId, { status: 'ACCEPTED' }) as Proposal;
      // Todas as restantes propostas do mesmo pedido passam a SUPERSEDED — o
      // servidor decide isto, nunca o cliente infere localmente (CLAUDE.md §4).
      mockDb.proposals
        .filter((p) => p.requestId === proposal.requestId && p.id !== proposalId && p.status === 'SENT')
        .forEach((p) => mockDb.updateProposal(p.id, { status: 'SUPERSEDED' }));
      mockDb.updateRequest(proposal.requestId, { status: 'CONFIRMED' });
      return accepted;
    });
  },
};
