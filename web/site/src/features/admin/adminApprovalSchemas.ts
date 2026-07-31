import { z } from 'zod';

/**
 * Validação de conveniência no cliente (mesmo estilo de
 * `features/auth/authSchemas.ts`) — a autoridade é sempre o servidor:
 * `reason` em falta para `REJECTED`/`SUSPENDED` continua a devolver `422`
 * mesmo que este schema não o apanhe primeiro (ex. drift entre contrato e
 * cliente). `decision` usa o enum de 3 valores do contrato
 * (`ProviderApprovalDecision`) — `PENDING` nunca é uma opção, porque não é
 * um destino válido em nenhuma transição.
 *
 * `providerId` é introduzido à mão pelo administrador: o contrato não tem
 * (ainda) um `GET` para listar prestadores `PENDING` nem para ver o detalhe
 * de um prestador que não esteja publicamente visível (ver
 * `features/admin/AdminApprovalConsole.tsx` para o porquê e o pedido ao
 * `api-contract`) — por isso não há forma de o pré-preencher ou de o
 * validar contra uma lista conhecida no cliente.
 */
export const decisionSchema = z
  .object({
    providerId: z.string().uuid('Introduza um identificador de prestador válido (UUID).'),
    decision: z.enum(['APPROVED', 'REJECTED', 'SUSPENDED']),
    reason: z
      .string()
      .trim()
      .max(2000, 'O motivo tem no máximo 2000 caracteres.')
      .optional()
      .or(z.literal('')),
  })
  .refine((value) => value.decision === 'APPROVED' || Boolean(value.reason && value.reason.length > 0), {
    message: 'O motivo é obrigatório para rejeitar ou suspender — o servidor recusa a decisão sem ele (422).',
    path: ['reason'],
  });

export type DecisionFormValues = z.infer<typeof decisionSchema>;
