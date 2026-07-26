import express from 'express';
import { randomUUID } from 'node:crypto';

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

const CATEGORY = { id: 'cat-canalizacao', parentId: null, slug: 'canalizacao', name: 'Canalização', active: true };

const requests = new Map<string, ServiceRequestRecord>();
const proposalsByRequest = new Map<string, ProposalRecord[]>();

function problem(status: number, slug: string, title: string, detail?: string) {
  return {
    type: `https://errors.servimatch.pt/${slug}`,
    title,
    status,
    ...(detail ? { detail } : {}),
  };
}

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

  return app;
}

if (process.argv[1]?.endsWith('server.ts') || process.argv[1]?.endsWith('server.js')) {
  const port = Number(process.env.PORT ?? 8093);
  createMockBackend().listen(port, () => {
    // eslint-disable-next-line no-console
    console.log(`[mock-backend] a ouvir em http://localhost:${port}`);
  });
}
