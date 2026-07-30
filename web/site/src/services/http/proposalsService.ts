import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { ProposalsService } from '../interfaces';

export const proposalsServiceHttp: ProposalsService = {
  async listForRequest(requestId, params) {
    const { data, error } = await api.GET('/v1/requests/{requestId}/proposals', {
      params: { path: { requestId }, query: { limit: params?.limit, cursor: params?.cursor } },
    });
    if (error) throwProblem(error);
    return data;
  },
  async listMine(params) {
    // GET /v1/proposals/me não aceita filtro por `status` no contrato — só
    // `limit`/`cursor`. Não se inventa aqui um parâmetro que o servidor
    // ignoraria silenciosamente.
    const { data, error } = await api.GET('/v1/proposals/me', {
      params: { query: { limit: params?.limit, cursor: params?.cursor } },
    });
    if (error) throwProblem(error);
    return data;
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
