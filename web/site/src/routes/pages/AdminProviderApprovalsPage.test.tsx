import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { jsonResponse, methodOf, problemResponse, urlOf } from '../../test/mockFetch';
import { AdminProviderApprovalsPage } from './AdminProviderApprovalsPage';

const PROVIDER_ID = 'a419d818-cc7f-4910-a99b-c14cb157f3eb';

function installFetchMock(role: 'ADMIN' | 'CUSTOMER', decisionOutcome: 'success' | 'conflict' | 'validation') {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = urlOf(input);
    const method = methodOf(input, init);

    if (url.startsWith('/auth/me')) {
      return jsonResponse({
        authenticated: true,
        user: { sub: 'admin-1', email: 'admin@servimatch.pt', username: 'admin', roles: [role] },
      });
    }

    if (url.includes(`/api/v1/admin/providers/${PROVIDER_ID}/approval`) && method === 'PATCH') {
      if (decisionOutcome === 'conflict') {
        return problemResponse(409, 'https://errors.servimatch.pt/conflict', 'Transição inválida.');
      }
      if (decisionOutcome === 'validation') {
        return new Response(
          JSON.stringify({
            type: 'https://errors.servimatch.pt/validation',
            title: 'Dados inválidos',
            status: 422,
            errors: [{ field: 'reason', message: 'O motivo é obrigatório para esta decisão.' }],
          }),
          { status: 422, headers: { 'content-type': 'application/problem+json' } },
        );
      }
      return jsonResponse({
        providerId: PROVIDER_ID,
        approvalStatus: 'APPROVED',
        reason: null,
        decidedBy: 'admin-1',
        decidedAt: '2026-07-25T10:00:00Z',
      });
    }

    return problemResponse(404, 'https://errors.servimatch.pt/not-found', 'Não encontrado');
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/admin" element={<AdminProviderApprovalsPage />} />
    </Routes>,
    { route: '/admin' },
  );
}

describe('AdminProviderApprovalsPage — consola de aprovação (defeito C1)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('regista uma aprovação com sucesso e mostra o resultado do servidor', async () => {
    installFetchMock('ADMIN', 'success');
    const user = userEvent.setup();

    renderPage();

    await waitFor(() => expect(screen.getByLabelText(/identificador do prestador/i)).toBeInTheDocument());
    await user.type(screen.getByLabelText(/identificador do prestador/i), PROVIDER_ID);
    await user.click(screen.getByRole('radio', { name: /^aprovar$/i }));
    await user.click(screen.getByRole('button', { name: /registar decisão/i }));

    await waitFor(() => expect(screen.getByText('APPROVED')).toBeInTheDocument());
    expect(screen.getByText(/decidido por/i)).toBeInTheDocument();
  });

  it('exige motivo no cliente para rejeitar/suspender, sem chegar a chamar o servidor', async () => {
    const fetchMock = installFetchMock('ADMIN', 'success');
    const user = userEvent.setup();

    renderPage();

    await waitFor(() => expect(screen.getByLabelText(/identificador do prestador/i)).toBeInTheDocument());
    await user.type(screen.getByLabelText(/identificador do prestador/i), PROVIDER_ID);
    await user.click(screen.getByRole('radio', { name: /^rejeitar$/i }));
    await user.click(screen.getByRole('button', { name: /registar decisão/i }));

    await waitFor(() => expect(screen.getByText(/o motivo é obrigatório/i)).toBeInTheDocument());
    expect(fetchMock.mock.calls.some((call) => methodOf(call[0], call[1] as RequestInit) === 'PATCH')).toBe(false);
  });

  it('409 do servidor (transição inválida) aparece como erro, sem rebentar a página', async () => {
    installFetchMock('ADMIN', 'conflict');
    const user = userEvent.setup();

    renderPage();

    await waitFor(() => expect(screen.getByLabelText(/identificador do prestador/i)).toBeInTheDocument());
    await user.type(screen.getByLabelText(/identificador do prestador/i), PROVIDER_ID);
    await user.click(screen.getByRole('radio', { name: /^aprovar$/i }));
    await user.click(screen.getByRole('button', { name: /registar decisão/i }));

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    // A página continua utilizável: o formulário ainda lá está.
    expect(screen.getByRole('button', { name: /registar decisão/i })).toBeEnabled();
  });

  it('422 do servidor mapeia para o campo motivo (rede de segurança contra drift cliente/contrato)', async () => {
    installFetchMock('ADMIN', 'validation');
    const user = userEvent.setup();

    renderPage();

    await waitFor(() => expect(screen.getByLabelText(/identificador do prestador/i)).toBeInTheDocument());
    await user.type(screen.getByLabelText(/identificador do prestador/i), PROVIDER_ID);
    await user.click(screen.getByRole('radio', { name: /^aprovar$/i }));
    await user.click(screen.getByRole('button', { name: /registar decisão/i }));

    await waitFor(() => expect(screen.getByText(/o motivo é obrigatório para esta decisão/i)).toBeInTheDocument());
  });
});
