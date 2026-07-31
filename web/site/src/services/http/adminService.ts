import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { AdminService } from '../interfaces';

/**
 * Única operação `Admin` do contrato hoje: `decideProviderApproval`
 * (`openapi.yaml:800`). O servidor continua a ser a autoridade sobre a
 * transição — este serviço só transporta a decisão, nunca a valida a
 * mais (ver `features/admin/adminApprovalSchemas.ts`).
 */
export const adminServiceHttp: AdminService = {
  async decideProviderApproval(providerId, body, idempotencyKey) {
    const { data, error } = await api.PATCH('/v1/admin/providers/{providerId}/approval', {
      params: {
        path: { providerId },
        header: { 'Idempotency-Key': idempotencyKey },
      },
      body,
    });
    if (error) throwProblem(error);
    return data;
  },
};
