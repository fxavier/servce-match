import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { SubscriptionsService } from '../interfaces';

export const subscriptionsServiceHttp: SubscriptionsService = {
  async listPlans() {
    const { data, error } = await api.GET('/v1/subscription-plans');
    if (error) throwProblem(error);
    return data;
  },
  current() {
    // Gap #3 — sem GET /v1/subscriptions/me; devolve "sem subscrição
    // conhecida" em vez de inventar um estado. A UI trata isto como
    // "por confirmar", nunca como PENDING/ACTIVE assumido no cliente.
    return Promise.resolve(undefined);
  },
  async create(body) {
    const { data, error } = await api.POST('/v1/subscriptions', { body });
    if (error) throwProblem(error);
    return data;
  },
};
