import { throwProblem } from '../../lib/problem';

/**
 * Alguns ecrãs deste site precisam de dados que `docs/api/openapi.yaml`
 * ainda não expõe (ver `services/domainTypes.ts` para a lista completa de
 * lacunas reportadas). Em vez de inventar um endpoint ou um campo, a
 * implementação HTTP real lança este `ProblemDetails` explícito — nunca
 * silenciosamente `undefined` nem dados inventados no cliente.
 */
export function notImplementedInContract(capability: string): never {
  throwProblem({
    type: 'https://errors.servimatch.pt/not-implemented-in-contract',
    title: 'Funcionalidade ainda não exposta pelo contrato da API.',
    status: 501,
    detail: `"${capability}" precisa de um endpoint novo em docs/api/openapi.yaml (pedido pendente ao api-contract). Em modo mock (VITE_USE_MOCKS=true) esta funcionalidade está totalmente simulada.`,
  });
}
