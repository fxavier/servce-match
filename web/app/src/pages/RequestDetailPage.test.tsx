import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { server } from '../test/mocks/server';
import { RequestDetailPage } from './RequestDetailPage';

function renderPage(route = '/requests/req-1') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path="/requests/:requestId" element={<RequestDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

const baseRequest = {
  id: 'req-1',
  customerId: 'user-123',
  title: 'Fuga na cozinha',
  status: 'PUBLISHED',
  proposalCount: 1,
  createdAt: '2026-07-01T10:00:00Z',
};

const sentProposal = {
  id: 'prop-1',
  requestId: 'req-1',
  providerId: 'prov-1',
  providerSummary: { id: 'prov-1', displayName: 'Canalizações Silva', ratingAvg: 4.8, ratingCount: 12 },
  price: { amountCents: 5500, currency: 'EUR' },
  description: 'Posso ir amanhã de manhã.',
  leadTimeDays: 1,
  status: 'SENT',
  createdAt: '2026-07-02T09:00:00Z',
};

describe('RequestDetailPage — ver propostas e aceitar (caminho principal)', () => {
  it('lista propostas com dinheiro formatado em pt-PT e aceita uma proposta', async () => {
    server.use(
      http.get('/api/v1/requests/:requestId', () => HttpResponse.json(baseRequest)),
      http.get('/api/v1/requests/:requestId/proposals', () =>
        HttpResponse.json({ items: [sentProposal], page: { nextCursor: null } }),
      ),
      http.post('/api/v1/proposals/:proposalId/accept', ({ params }) =>
        HttpResponse.json({ ...sentProposal, id: params.proposalId, status: 'ACCEPTED' }),
      ),
    );

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Fuga na cozinha' })).toBeInTheDocument();
    expect(await screen.findByText('Canalizações Silva')).toBeInTheDocument();
    expect(screen.getByText('55,00 €')).toBeInTheDocument();

    // Depois de aceitar, a página volta a pedir a lista ao servidor (nunca
    // infere o novo estado localmente) — simula o servidor a devolver
    // ACCEPTED na releitura.
    server.use(
      http.get('/api/v1/requests/:requestId/proposals', () =>
        HttpResponse.json({
          items: [{ ...sentProposal, status: 'ACCEPTED' }],
          page: { nextCursor: null },
        }),
      ),
    );

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /aceitar proposta/i }));

    const item = await screen.findByText('Canalizações Silva');
    expect(within(item.closest('li')!).getByText(/Estado: ACCEPTED/)).toBeInTheDocument();
    expect(within(item.closest('li')!).queryByRole('button', { name: /aceitar proposta/i })).not.toBeInTheDocument();
  });
});

describe('RequestDetailPage — erro ao carregar propostas (caso de erro)', () => {
  it('mostra o Problem Details quando o servidor recusa o acesso', async () => {
    server.use(
      http.get('/api/v1/requests/:requestId', () => HttpResponse.json(baseRequest)),
      http.get('/api/v1/requests/:requestId/proposals', () =>
        HttpResponse.json(
          {
            type: 'https://errors.servimatch.pt/forbidden',
            title: 'Sem permissão para ver estas propostas',
            status: 403,
          },
          { status: 403, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    renderPage();

    expect(await screen.findByText('Sem permissão para ver estas propostas')).toBeInTheDocument();
  });
});
