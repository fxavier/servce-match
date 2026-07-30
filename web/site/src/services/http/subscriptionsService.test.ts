import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, problemResponse } from '../../test/mockFetch';
import { subscriptionsServiceHttp } from './subscriptionsService';

/**
 * `GET /v1/subscriptions/me` devolve 404 quando o prestador nunca
 * subscreveu nenhum plano — deliberadamente sem valor `NONE` no enum
 * (docs/api/openapi.yaml). Este teste garante que o 404 vira "sem
 * subscrição" (`undefined`), nunca um erro genérico borbulhado ao chamador.
 */
describe('subscriptionsServiceHttp.current', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('devolve undefined em 404 (nunca subscreveu)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        problemResponse(404, 'https://errors.servimatch.pt/not-found', 'O prestador autenticado nunca subscreveu nenhum plano.'),
      ),
    );

    await expect(subscriptionsServiceHttp.current()).resolves.toBeUndefined();
  });

  it('devolve a subscrição em 200', async () => {
    const subscription = {
      id: 'sub-1',
      providerId: 'p-0001',
      planId: 'plan-1',
      status: 'ACTIVE',
      currentPeriodStart: '2026-07-01T00:00:00Z',
      currentPeriodEnd: '2026-08-01T00:00:00Z',
      cancelAtPeriodEnd: false,
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(() => jsonResponse(subscription)),
    );

    await expect(subscriptionsServiceHttp.current()).resolves.toEqual(subscription);
  });

  it('lança ProblemDetails para erros que não sejam 404', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => problemResponse(401, 'https://errors.servimatch.pt/unauthorized', 'Não autenticado.')),
    );

    await expect(subscriptionsServiceHttp.current()).rejects.toMatchObject({ status: 401 });
  });
});
