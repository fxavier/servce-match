import type { RequestsService } from '../interfaces';
import type { ServiceRequest } from '../types';
import { categoryById } from './fixtures/categories';
import { PROVIDER_CATEGORY_IDS } from './fixtures/providers';
import { mockCurrentUser } from './currentUser';
import { mockDb } from './db';
import { withLatency } from './latency';
import { throwProblem } from './mockProblem';

function paginate(items: ServiceRequest[], limit = 20, cursor?: string) {
  const offset = cursor ? Number(cursor) : 0;
  const page = items.slice(offset, offset + limit);
  const nextOffset = offset + limit;
  return { items: page, page: { nextCursor: nextOffset < items.length ? String(nextOffset) : null } };
}

export const requestsServiceMock: RequestsService = {
  listMine(params) {
    return withLatency(() => {
      const user = mockCurrentUser.get();
      let items = mockDb.requests.filter((request) => request.customerId === (user?.id ?? 'c-0001'));
      if (params?.status) items = items.filter((request) => request.status === params.status);
      return paginate(items, params?.limit, params?.cursor);
    });
  },
  get(id) {
    return withLatency(() => {
      const request = mockDb.requests.find((item) => item.id === id);
      if (!request) throwProblem({ type: 'https://errors.servimatch.pt/not-found', title: 'Pedido não encontrado.', status: 404 });
      return request;
    });
  },
  create(body) {
    return withLatency(() => {
      const user = mockCurrentUser.get();
      const request: ServiceRequest = {
        id: `r-${crypto.randomUUID().slice(0, 8)}`,
        customerId: user?.id ?? 'c-0001',
        category: categoryById(body.categoryId),
        title: body.title,
        description: body.description,
        address: body.address,
        urgency: body.urgency,
        availability: body.availability,
        status: 'DRAFT',
        images: [],
        proposalCount: 0,
        createdAt: new Date().toISOString(),
        publishedAt: null,
      };
      mockDb.addRequest(request);
      return request;
    });
  },
  publish(id) {
    return withLatency(() => {
      const existing = mockDb.requests.find((request) => request.id === id);
      if (!existing) throwProblem({ type: 'https://errors.servimatch.pt/not-found', title: 'Pedido não encontrado.', status: 404 });
      if (existing.status !== 'DRAFT') {
        throwProblem({
          type: 'https://errors.servimatch.pt/conflict',
          title: 'Este pedido já foi publicado.',
          status: 409,
          detail: `O pedido está em estado "${existing.status}" e não pode ser publicado novamente.`,
        });
      }
      const updated = mockDb.updateRequest(id, { status: 'PUBLISHED', publishedAt: new Date().toISOString() });
      return updated as ServiceRequest;
    });
  },
  listProviderInbox(params) {
    return withLatency(() => {
      const user = mockCurrentUser.get();
      if (!mockDb.providerSubscriptionActive) {
        throwProblem({
          type: 'https://errors.servimatch.pt/subscription-required',
          title: 'É preciso uma subscrição ativa para veres pedidos elegíveis.',
          status: 403,
          detail: 'Escolhe um plano para começares a receber pedidos na tua zona e categorias.',
        });
      }
      const categoryIds = user ? (PROVIDER_CATEGORY_IDS[user.id] ?? []) : [];
      let items = mockDb.requests.filter(
        (request) => request.status === 'PUBLISHED' && categoryIds.includes(request.category?.id ?? ''),
      );
      if (params?.status) items = items.filter((request) => request.status === params.status);
      return paginate(items, params?.limit, params?.cursor);
    });
  },
};
