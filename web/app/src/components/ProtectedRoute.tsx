import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { LoadingState } from './LoadingState';

/**
 * Só esconde/mostra UI — nunca é a autoridade sobre o acesso (CLAUDE.md §4).
 * Cada pedido feito através de `src/api/client.ts` é reautorizado pelo
 * backend de qualquer forma; isto existe só para não mostrar formulários a
 * quem sabidamente ainda não tem sessão.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <LoadingState label="A verificar sessão…" />;
  }

  if (status === 'unauthenticated') {
    const returnTo = `${location.pathname}${location.search}`;
    return <Navigate to={`/login?returnTo=${encodeURIComponent(returnTo)}`} replace />;
  }

  return <>{children}</>;
}
