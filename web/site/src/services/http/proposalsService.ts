import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { ProposalsService } from '../interfaces';
import { notImplementedInContract } from './notImplemented';

export const proposalsServiceHttp: ProposalsService = {
  async listForRequest(requestId, params) {
    const { data, error } = await api.GET('/v1/requests/{requestId}/proposals', {
      params: { path: { requestId }, query: { limit: params?.limit, cursor: params?.cursor } },
    });
    if (error) throwProblem(error);
    return data;
  },
  listMine() {
    // Gap #7 — sem endpoint "as minhas propostas" (prestador) no contrato.
    return notImplementedInContract('lista das minhas propostas enviadas');
  },
  async create(requestId, body) {
    const { data, error } = await api.POST('/v1/requests/{requestId}/proposals', {
      params: { path: { requestId } },
      body,
    });
    if (error) throwProblem(error);
    return data;
  },
  async accept(proposalId) {
    const { data, error } = await api.POST('/v1/proposals/{proposalId}/accept', {
      params: { path: { proposalId } },
    });
    if (error) throwProblem(error);
    return data;
  },
};
