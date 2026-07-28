import { PROPOSALS } from './fixtures/proposals';
import { REQUESTS } from './fixtures/requests';
import type { Proposal, ServiceRequest } from '../types';

/**
 * Estado mutável da sessão de mocks — clona as fixtures (que ficam imutáveis
 * e reutilizáveis nos testes) para que `create`/`publish`/`accept` possam
 * alterar o "backend" simulado sem afetar outros módulos que importem as
 * fixtures diretamente.
 */
let requests: ServiceRequest[] = structuredClone(REQUESTS);
let proposals: Proposal[] = structuredClone(PROPOSALS);

/**
 * Estado de subscrição do prestador atualmente autenticado no mock — o
 * toggle de dev "Simular subscrição ativa" (§7) mexe aqui. Nunca confundir
 * com autoridade real: em produção isto é sempre decidido pelo servidor.
 */
let providerSubscriptionActive = true;

export const mockDb = {
  get requests() {
    return requests;
  },
  get proposals() {
    return proposals;
  },
  get providerSubscriptionActive() {
    return providerSubscriptionActive;
  },
  setProviderSubscriptionActive(value: boolean) {
    providerSubscriptionActive = value;
  },
  addRequest(request: ServiceRequest) {
    requests = [request, ...requests];
  },
  updateRequest(id: string, patch: Partial<ServiceRequest>) {
    requests = requests.map((request) => (request.id === id ? { ...request, ...patch } : request));
    return requests.find((request) => request.id === id);
  },
  addProposal(proposal: Proposal) {
    proposals = [proposal, ...proposals];
  },
  updateProposal(id: string, patch: Partial<Proposal>) {
    proposals = proposals.map((proposal) => (proposal.id === id ? { ...proposal, ...patch } : proposal));
    return proposals.find((proposal) => proposal.id === id);
  },
  /** Só para testes — repõe o estado inicial entre casos. */
  reset() {
    requests = structuredClone(REQUESTS);
    proposals = structuredClone(PROPOSALS);
    providerSubscriptionActive = true;
  },
};
