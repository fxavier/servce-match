import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import { AppRouter } from './router';

/** Smoke de renderização das rotas públicas principais (§10). */
describe.each([
  '/',
  '/como-funciona',
  '/categorias',
  '/prestadores',
  '/precos',
  '/sobre',
  '/contactos',
  '/faq',
  '/termos',
  '/privacidade',
  '/rota-que-nao-existe',
])('rota pública %s', (route) => {
  it('renderiza sem rebentar, com <title> definido e exatamente um <h1>', async () => {
    renderWithProviders(<AppRouter />, { route });

    // A landing agrega muitos subcomponentes (marketing + motion) — o
    // primeiro import lazy em ambiente de teste pode demorar mais que o
    // timeout por omissão do `waitFor`.
    await waitFor(() => expect(document.title).toContain('ServiMatch'), { timeout: 5000 });
    await waitFor(() => expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1), { timeout: 5000 });
  });
});
