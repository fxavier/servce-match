import { screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '../test/renderWithProviders';
import { jsonResponse, urlOf } from '../test/mockFetch';
import { ProtectedRoute } from './ProtectedRoute';

function Protected() {
  return <ProtectedRoute>{'privado'}</ProtectedRoute>;
}

function LoginProbe() {
  return <p>ecrã de login</p>;
}

function AdminOnly() {
  return <ProtectedRoute roles={['ADMIN']}>{'consola de administração'}</ProtectedRoute>;
}

function stubSession(roles: string[]) {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      if (urlOf(input).startsWith('/auth/me')) {
        return jsonResponse({
          authenticated: true,
          user: { sub: 'u-1', email: 'u@example.com', username: 'u', roles },
        });
      }
      return jsonResponse({ authenticated: false });
    }),
  );
}

describe('ProtectedRoute', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('redireciona para /entrar com returnTo sanitizado quando não autenticado', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/pedidos/novo" element={<Protected />} />
        <Route path="/entrar" element={<LoginProbe />} />
      </Routes>,
      { route: '/pedidos/novo?categoria=canalizacao' },
    );

    await waitFor(() => expect(screen.getByText('ecrã de login')).toBeInTheDocument());
  });

  describe('gating por role (só esconde a UI — a autoridade é o servidor, ver comentário do componente)', () => {
    beforeEach(() => {
      stubSession(['CUSTOMER']);
    });

    it('esconde a UI de um utilizador sem o role exigido (ex.: CUSTOMER em /admin)', async () => {
      renderWithProviders(
        <Routes>
          <Route path="/admin" element={<AdminOnly />} />
        </Routes>,
        { route: '/admin' },
      );

      await waitFor(() =>
        expect(screen.getByRole('heading', { name: /não tens acesso a esta página/i })).toBeInTheDocument(),
      );
      expect(screen.queryByText('consola de administração')).not.toBeInTheDocument();
    });
  });

  it('deixa passar um utilizador com o role exigido', async () => {
    stubSession(['ADMIN']);

    renderWithProviders(
      <Routes>
        <Route path="/admin" element={<AdminOnly />} />
      </Routes>,
      { route: '/admin' },
    );

    await waitFor(() => expect(screen.getByText('consola de administração')).toBeInTheDocument());
  });
});
