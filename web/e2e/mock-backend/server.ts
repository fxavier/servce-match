import express from 'express';
import { randomUUID } from 'node:crypto';
import { SEED_PENDING_PROVIDER_ID } from '../fixtures.js';

/**
 * Backend de domínio falso para o E2E — implementa só os endpoints do
 * contrato (docs/api/openapi.yaml) usados no fluxo crítico: autenticar →
 * publicar pedido → ver propostas → aceitar proposta. Formas de resposta
 * derivadas dos schemas do contrato, não inventadas.
 */

interface ServiceRequestRecord {
  id: string;
  customerId: string;
  category: { id: string; parentId: null; slug: string; name: string; active: boolean };
  title: string;
  description?: string;
  address: Record<string, unknown>;
  urgency?: string;
  availability?: string;
  status: string;
  images: unknown[];
  proposalCount: number;
  createdAt: string;
  publishedAt: string | null;
}

interface ProposalRecord {
  id: string;
  requestId: string;
  providerId: string;
  providerSummary: {
    id: string;
    displayName: string;
    ratingAvg: number;
    ratingCount: number;
    verified: boolean;
    premiumBadge: boolean;
  };
  price: { amountCents: number; currency: string };
  description: string;
  leadTimeDays: number;
  status: string;
  createdAt: string;
}

/**
 * `approval_status` de um prestador falso (defeito C1,
 * `docs/ESTADO-DO-SISTEMA.md`). Só o suficiente para exercitar
 * `PATCH /v1/admin/providers/{id}/approval` (`decideProviderApproval`,
 * `openapi.yaml:800`) — este mock não tem `GET` de listagem/detalhe porque
 * o contrato real também não tem (lacuna reportada, ver
 * `web/site/src/features/admin/AdminApprovalConsole.tsx`).
 */
interface ProviderApprovalRecord {
  providerId: string;
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
  reason: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
}

const CATEGORY = { id: 'cat-canalizacao', parentId: null, slug: 'canalizacao', name: 'Canalização', active: true };

const requests = new Map<string, ServiceRequestRecord>();
const proposalsByRequest = new Map<string, ProposalRecord[]>();

// Prestador seed, fixo, para o E2E de aprovação (evita depender de um
// `POST /v1/providers` que não existe no fluxo crítico já coberto por
// `main-flow.spec.ts`). Começa `PENDING`, como qualquer prestador real.
// `SEED_PENDING_PROVIDER_ID` vem de `../fixtures.ts` (UUID, porque
// `providerId` é validado como UUID no cliente —
// `features/admin/adminApprovalSchemas.ts`) para que
// `web/e2e/tests/admin-approval.spec.ts` use o mesmo valor sem importar
// este módulo (que arrancaria o servidor uma segunda vez — ver `fixtures.ts`).
const providerApprovals = new Map<string, ProviderApprovalRecord>([
  [
    SEED_PENDING_PROVIDER_ID,
    { providerId: SEED_PENDING_PROVIDER_ID, approvalStatus: 'PENDING', reason: null, decidedBy: null, decidedAt: null },
  ],
]);

const VALID_TRANSITIONS: Record<string, ('APPROVED' | 'REJECTED' | 'SUSPENDED')[]> = {
  PENDING: ['APPROVED', 'REJECTED'],
  APPROVED: ['SUSPENDED'],
};

function problem(status: number, slug: string, title: string, detail?: string) {
  return {
    type: `https://errors.servimatch.pt/${slug}`,
    title,
    status,
    ...(detail ? { detail } : {}),
  };
}

/**
 * Extrai `sub`/`realm_access.roles` do `access_token` que o BFF reencaminha
 * em `Authorization: Bearer` (`web/bff/src/proxy.ts` — nunca reencaminha o
 * cookie de sessão, só o token). Decodificação sem verificação de
 * assinatura: este processo não tem o JWKS do `mock-oidc` (nem devia
 * precisar — não é o que este mock testa); é suficiente para simular a
 * verificação de `ROLE_ADMIN` que o backend real faz com
 * `@PreAuthorize("hasRole('ADMIN')")` antes de tocar em qualquer dado
 * (ONDA-C1 §1 — "role verificada antes de carregar o alvo, para não criar
 * oráculo 403/404").
 */
function claimsFromAuthorization(req: express.Request): { sub?: string; roles: string[] } {
  const header = req.header('authorization') ?? '';
  const token = header.startsWith('Bearer ') ? header.slice('Bearer '.length) : undefined;
  const payloadSegment = token?.split('.')[1];
  if (!payloadSegment) return { roles: [] };
  try {
    const json = Buffer.from(payloadSegment, 'base64url').toString('utf8');
    const payload = JSON.parse(json) as { sub?: string; realm_access?: { roles?: string[] } };
    return { sub: payload.sub, roles: payload.realm_access?.roles ?? [] };
  } catch {
    return { roles: [] };
  }
}

/** Resultados de decisões já aplicadas, por `Idempotency-Key` — uma repetição devolve o mesmo resultado em vez de reaplicar a transição. */
const idempotentDecisions = new Map<string, { status: number; body: unknown }>();

export function createMockBackend() {
  const app = express();
  app.use(express.json());

  app.get('/v1/categories', (_req, res) => {
    res.json([CATEGORY]);
  });

  app.post('/v1/requests', (req, res) => {
    const { categoryId, title, description, address, urgency, availability } = req.body ?? {};
    if (!categoryId || !title || !address) {
      res
        .status(422)
        .type('application/problem+json')
        .json(problem(422, 'validation', 'Dados inválidos', 'categoryId, title e address são obrigatórios.'));
      return;
    }

    const id = randomUUID();
    const record: ServiceRequestRecord = {
      id,
      customerId: 'e2e-customer-1',
      category: CATEGORY,
      title,
      description,
      address,
      urgency,
      availability,
      status: 'DRAFT',
      images: [],
      proposalCount: 0,
      createdAt: new Date().toISOString(),
      publishedAt: null,
    };
    requests.set(id, record);
    proposalsByRequest.set(id, []);

    res.status(201).location(`/v1/requests/${id}`).json(record);
  });

  app.get('/v1/requests/:requestId', (req, res) => {
    const record = requests.get(req.params.requestId);
    if (!record) {
      res.status(404).type('application/problem+json').json(problem(404, 'not-found', 'Pedido não encontrado.'));
      return;
    }
    res.json(record);
  });

  app.post('/v1/requests/:requestId/publish', (req, res) => {
    const record = requests.get(req.params.requestId);
    if (!record) {
      res.status(404).type('application/problem+json').json(problem(404, 'not-found', 'Pedido não encontrado.'));
      return;
    }
    if (record.status !== 'DRAFT') {
      res
        .status(409)
        .type('application/problem+json')
        .json(problem(409, 'invalid-state-transition', 'O pedido já foi publicado.'));
      return;
    }

    record.status = 'PUBLISHED';
    record.publishedAt = new Date().toISOString();

    // Simula o matching assíncrono + um prestador a responder: sem isto o
    // teste E2E teria de implementar também o lado do prestador só para
    // popular uma proposta, o que está fora do âmbito desta onda.
    const proposal: ProposalRecord = {
      id: randomUUID(),
      requestId: record.id,
      providerId: 'e2e-provider-1',
      providerSummary: {
        id: 'e2e-provider-1',
        displayName: 'Canalizações Silva',
        ratingAvg: 4.8,
        ratingCount: 37,
        verified: true,
        premiumBadge: true,
      },
      price: { amountCents: 7500, currency: 'EUR' },
      description: 'Posso ir já amanhã de manhã resolver a fuga.',
      leadTimeDays: 1,
      status: 'SENT',
      createdAt: new Date().toISOString(),
    };
    proposalsByRequest.set(record.id, [proposal]);
    record.proposalCount = 1;

    res.json(record);
  });

  app.get('/v1/requests/:requestId/proposals', (req, res) => {
    if (!requests.has(req.params.requestId)) {
      res.status(404).type('application/problem+json').json(problem(404, 'not-found', 'Pedido não encontrado.'));
      return;
    }
    const items = proposalsByRequest.get(req.params.requestId) ?? [];
    res.json({ items, page: { nextCursor: null } });
  });

  app.post('/v1/proposals/:proposalId/accept', (req, res) => {
    for (const [requestId, proposals] of proposalsByRequest.entries()) {
      const proposal = proposals.find((p) => p.id === req.params.proposalId);
      if (proposal) {
        proposal.status = 'ACCEPTED';
        for (const other of proposals) {
          if (other.id !== proposal.id) other.status = 'SUPERSEDED';
        }
        const record = requests.get(requestId);
        if (record) record.status = 'CONFIRMED';
        res.json(proposal);
        return;
      }
    }
    res.status(404).type('application/problem+json').json(problem(404, 'not-found', 'Proposta não encontrada.'));
  });

  // PATCH /v1/admin/providers/{providerId}/approval (decideProviderApproval,
  // openapi.yaml:800) — defeito C1. Único endpoint `Admin` do contrato hoje.
  app.patch('/v1/admin/providers/:providerId/approval', (req, res) => {
    const { sub, roles } = claimsFromAuthorization(req);
    if (!roles.includes('ADMIN')) {
      // Role verificada ANTES de tocar no prestador — nunca 404 primeiro
      // (evitaria um oráculo 403/404 sobre a existência do recurso).
      res.status(403).type('application/problem+json').json(problem(403, 'forbidden', 'Sem permissão de administrador.'));
      return;
    }

    const record = providerApprovals.get(req.params.providerId);
    if (!record) {
      res.status(404).type('application/problem+json').json(problem(404, 'not-found', 'Prestador não encontrado.'));
      return;
    }

    const idempotencyKey = req.header('idempotency-key');
    const idempotencyCacheKey = idempotencyKey ? `${req.params.providerId}:${idempotencyKey}` : undefined;
    if (idempotencyCacheKey) {
      const cached = idempotentDecisions.get(idempotencyCacheKey);
      if (cached) {
        res.status(cached.status).json(cached.body);
        return;
      }
    }

    const body = (req.body ?? {}) as { decision?: string; reason?: string };
    const decision = body.decision;
    if (decision !== 'APPROVED' && decision !== 'REJECTED' && decision !== 'SUSPENDED') {
      res
        .status(400)
        .type('application/problem+json')
        .json(problem(400, 'validation', 'Dados inválidos', 'decision tem de ser APPROVED, REJECTED ou SUSPENDED.'));
      return;
    }

    const reason = typeof body.reason === 'string' && body.reason.trim().length > 0 ? body.reason.trim() : undefined;
    if ((decision === 'REJECTED' || decision === 'SUSPENDED') && !reason) {
      res
        .status(422)
        .type('application/problem+json')
        .json({
          ...problem(422, 'validation', 'Dados inválidos', 'reason é obrigatório para REJECTED e SUSPENDED.'),
          errors: [{ field: 'reason', message: 'O motivo é obrigatório para esta decisão.' }],
        });
      return;
    }

    const allowedTargets = VALID_TRANSITIONS[record.approvalStatus] ?? [];
    if (!allowedTargets.includes(decision)) {
      res
        .status(409)
        .type('application/problem+json')
        .json(
          problem(
            409,
            'conflict',
            'Transição inválida.',
            `O prestador está em ${record.approvalStatus}; não é possível aplicar ${decision}.`,
          ),
        );
      return;
    }

    record.approvalStatus = decision;
    record.reason = reason ?? null;
    record.decidedBy = sub ?? 'e2e-admin-unknown';
    record.decidedAt = new Date().toISOString();

    const responseBody = {
      providerId: record.providerId,
      approvalStatus: record.approvalStatus,
      reason: record.reason,
      decidedBy: record.decidedBy,
      decidedAt: record.decidedAt,
    };
    if (idempotencyCacheKey) idempotentDecisions.set(idempotencyCacheKey, { status: 200, body: responseBody });
    res.status(200).json(responseBody);
  });

  return app;
}

if (process.argv[1]?.endsWith('server.ts') || process.argv[1]?.endsWith('server.js')) {
  const port = Number(process.env.PORT ?? 8093);
  createMockBackend().listen(port, () => {
    // eslint-disable-next-line no-console
    console.log(`[mock-backend] a ouvir em http://localhost:${port}`);
  });
}
