import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { SubscriptionsService } from '../interfaces';

export const subscriptionsServiceHttp: SubscriptionsService = {
  async listPlans() {
    const { data, error } = await api.GET('/v1/subscription-plans');
    if (error) throwProblem(error);
    return data;
  },
  async current() {
    const { data, error, response } = await api.GET('/v1/subscriptions/me');
    if (error) {
      // 404 é o valor de domínio "nunca subscreveu" — deliberadamente sem
      // `NONE` no enum (ver docs/api/openapi.yaml). Não deixar borbulhar
      // como erro genérico.
      if (response.status === 404) return undefined;
      throwProblem(error);
    }
    return data;
  },
  async create(body) {
    const { data, error } = await api.POST('/v1/subscriptions', { body });
    if (error) throwProblem(error);
    return data;
  },
};
