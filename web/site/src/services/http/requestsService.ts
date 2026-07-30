import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { RequestsService } from '../interfaces';

export const requestsServiceHttp: RequestsService = {
  async listMine(params) {
    const { data, error } = await api.GET('/v1/requests', {
      params: { query: { status: params?.status, limit: params?.limit, cursor: params?.cursor } },
    });
    if (error) throwProblem(error);
    return data;
  },
  async get(id) {
    const { data, error } = await api.GET('/v1/requests/{requestId}', { params: { path: { requestId: id } } });
    if (error) throwProblem(error);
    return data;
  },
  async create(body) {
    const { data, error } = await api.POST('/v1/requests', { body });
    if (error) throwProblem(error);
    return data;
  },
  async publish(id) {
    const { data, error } = await api.POST('/v1/requests/{requestId}/publish', {
      params: { path: { requestId: id } },
    });
    if (error) throwProblem(error);
    return data;
  },
  async listProviderInbox(params) {
    const { data, error } = await api.GET('/v1/providers/me/requests', {
      params: { query: { status: params?.status, limit: params?.limit, cursor: params?.cursor } },
    });
    if (error) throwProblem(error);
    return data;
  },
};
