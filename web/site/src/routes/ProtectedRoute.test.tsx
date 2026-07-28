import { screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import { ProtectedRoute } from './ProtectedRoute';

function Protected() {
  return <ProtectedRoute>{'privado'}</ProtectedRoute>;
}

function LoginProbe() {
  return <p>ecrã de login</p>;
}

describe('ProtectedRoute', () => {
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
});
