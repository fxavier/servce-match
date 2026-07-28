import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { mockCurrentUser } from '../../services/mock/currentUser';
import { mockDb } from '../../services/mock/db';
import { ProviderRequestDetailPage } from './ProviderRequestDetailPage';

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
    mockDb.reset();
    mockCurrentUser.set({ id: 'p-0001', roles: ['PROVIDER'] });
  });

  it('403 subscription-required renderiza o painel de upsell, não um erro genérico', async () => {
    mockDb.setProviderSubscriptionActive(false);
    const user = userEvent.setup();

    renderPage('r-0002');

    await waitFor(() => expect(screen.getByLabelText(/preço \(eur\)/i)).toBeInTheDocument());

    await user.type(screen.getByLabelText(/preço \(eur\)/i), '75,00');
    await user.type(screen.getByLabelText(/descrição do orçamento/i), 'Pintura completa, dois dias de trabalho.');
    await user.click(screen.getByRole('button', { name: /enviar orçamento/i }));

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /ative a sua subscrição para ver isto/i })).toBeInTheDocument(),
    );
    // Não é um alerta genérico — mostra os planos para resolver a situação.
    expect(screen.getAllByText(/professional/i).length).toBeGreaterThan(0);
    expect(screen.queryByText(/algo correu mal/i)).not.toBeInTheDocument();
  });

  it('com subscrição ativa, envia o orçamento com sucesso', async () => {
    mockDb.setProviderSubscriptionActive(true);
    const user = userEvent.setup();

    renderPage('r-0002');

    await waitFor(() => expect(screen.getByLabelText(/preço \(eur\)/i)).toBeInTheDocument());
    await user.type(screen.getByLabelText(/preço \(eur\)/i), '90,00');
    await user.type(screen.getByLabelText(/descrição do orçamento/i), 'Pintura completa, dois dias de trabalho.');
    await user.click(screen.getByRole('button', { name: /enviar orçamento/i }));

    await waitFor(() => expect(screen.getByText(/orçamento enviado com sucesso/i)).toBeInTheDocument());
  });
});
