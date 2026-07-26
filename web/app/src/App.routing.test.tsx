import { screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { server } from './test/mocks/server';
import { authenticatedSession } from './test/mocks/authHandlers';

describe('ProtectedRoute (via App)', () => {
  it('redireciona para /login quando não há sessão (caso de erro/estado não autenticado)', async () => {
    render(
      <MemoryRouter initialEntries={['/requests/new']}>
        <App />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: /entrar no servimatch/i })).toBeInTheDocument();
  });

  it('mostra a página protegida quando há sessão ativa', async () => {
    server.use(authenticatedSession());

    render(
      <MemoryRouter initialEntries={['/requests/new']}>
        <App />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: /publicar um pedido/i })).toBeInTheDocument();
  });
});
