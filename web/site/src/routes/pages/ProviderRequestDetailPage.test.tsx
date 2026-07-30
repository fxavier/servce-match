import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { jsonResponse, methodOf, problemResponse, urlOf } from '../../test/mockFetch';
import { ProviderRequestDetailPage } from './ProviderRequestDetailPage';

const PROVIDER_PROFILE = {
  id: 'p-0001',
  displayName: 'Rita Nogueira',
  headline: 'Pinturas e remodelações',
  ratingAvg: 4.6,
  ratingCount: 12,
  verified: true,
  premiumBadge: false,
  bio: 'Pinturas de interior e exterior.',
  categoryNames: ['Pinturas'],
  zones: [{ regionCode: 'PT-LIS', label: 'Lisboa' }],
  location: { lat: 38.7223, lon: -9.1393 },
  ratingDistribution: { 5: 6, 4: 4, 3: 2, 2: 0, 1: 0 },
  portfolioImageUrls: [],
  memberSince: '2025-01-01T00:00:00Z',
};

const REQUEST = {
  id: 'r-0002',
  customerId: 'c-0001',
  category: { id: 'cat-1', parentId: null, slug: 'pinturas', name: 'Pinturas', active: true },
  title: 'Pintar sala e corredor',
  description: 'Pintura completa, dois dias de trabalho previstos.',
  address: { city: 'Lisboa', postalCode: '1000-001' },
  urgency: 'NORMAL',
  status: 'PUBLISHED',
  images: [],
  proposalCount: 0,
  createdAt: '2026-07-20T10:00:00Z',
  publishedAt: '2026-07-20T10:05:00Z',
};

function installFetchMock(proposalOutcome: 'blocked' | 'success') {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = urlOf(input);
    const method = methodOf(input, init);

    if (url.startsWith('/auth/me')) {
      return jsonResponse({
        authenticated: true,
        user: { sub: 'p-0001', email: 'provider.test@servimatch.pt', username: 'provider.test', roles: ['PROVIDER'] },
      });
    }
    if (url.includes('/api/v1/requests/r-0002/proposals') && method === 'POST') {
      return proposalOutcome === 'blocked'
        ? problemResponse(403, 'https://errors.servimatch.pt/subscription-required', 'Subscrição necessária')
        : jsonResponse(
            {
              id: 'prop-1',
              requestId: 'r-0002',
              providerId: 'p-0001',
              price: { amountCents: 9000, currency: 'EUR' },
              description: 'Pintura completa, dois dias de trabalho.',
              status: 'SENT',
              createdAt: '2026-07-25T10:00:00Z',
            },
            { status: 201 },
          );
    }
    if (url.includes('/api/v1/requests/r-0002')) {
      return jsonResponse(REQUEST);
    }
    if (url.includes('/api/v1/providers/me')) {
      return jsonResponse(PROVIDER_PROFILE);
    }
    if (url.includes('/api/v1/subscription-plans')) {
      return jsonResponse([]);
    }
    return problemResponse(404, 'https://errors.servimatch.pt/not-found', 'Não encontrado');
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderPage(requestId: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/pro/pedidos/:requestId" element={<ProviderRequestDetailPage />} />
    </Routes>,
    { route: `/pro/pedidos/${requestId}` },
  );
}

describe('ProviderRequestDetailPage — formulário de orçamento', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('403 subscription-required renderiza o painel de upsell, não um erro genérico', async () => {
    installFetchMock('blocked');
    const user = userEvent.setup();

    renderPage('r-0002');

    await waitFor(() => expect(screen.getByLabelText(/preço \(eur\)/i)).toBeInTheDocument());

    await user.type(screen.getByLabelText(/preço \(eur\)/i), '75,00');
    await user.type(screen.getByLabelText(/descrição do orçamento/i), 'Pintura completa, dois dias de trabalho.');
    await user.click(screen.getByRole('button', { name: /enviar orçamento/i }));

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /ative a sua subscrição para ver isto/i })).toBeInTheDocument(),
    );
    expect(screen.queryByText(/algo correu mal/i)).not.toBeInTheDocument();
  });

  it('com subscrição ativa, envia o orçamento com sucesso', async () => {
    installFetchMock('success');
    const user = userEvent.setup();

    renderPage('r-0002');

    await waitFor(() => expect(screen.getByLabelText(/preço \(eur\)/i)).toBeInTheDocument());
    await user.type(screen.getByLabelText(/preço \(eur\)/i), '90,00');
    await user.type(screen.getByLabelText(/descrição do orçamento/i), 'Pintura completa, dois dias de trabalho.');
    await user.click(screen.getByRole('button', { name: /enviar orçamento/i }));

    await waitFor(() => expect(screen.getByText(/orçamento enviado com sucesso/i)).toBeInTheDocument());
  });
});
