import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { Lock } from 'lucide-react';
import { SkeletonText } from '../components/ui/Skeleton';
import { useAuth } from '../features/auth/useAuth';
import type { Role } from '../features/auth/types';

export interface ProtectedRouteProps {
  roles?: Role[];
  children: ReactNode;
}

/**
 * Só esconde/mostra UI — nunca é a autoridade sobre o acesso (CLAUDE.md §4).
 * Cada pedido feito através de `services/http.ts` é reautorizado pelo
 * backend de qualquer forma; isto existe só para não mostrar formulários a
 * quem sabidamente ainda não tem sessão ou o `role` certo.
 */
export function ProtectedRoute({ roles, children }: ProtectedRouteProps) {
  const { status, hasRole } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div className="mx-auto max-w-3xl px-5 py-16">
        <SkeletonText lines={4} />
      </div>
    );
  }

  if (status === 'unauthenticated') {
    const returnTo = `${location.pathname}${location.search}`;
    return <Navigate to={`/entrar?returnTo=${encodeURIComponent(returnTo)}`} replace />;
  }

  if (roles && roles.length > 0 && !roles.some((role) => hasRole(role))) {
    return <ForbiddenPanel />;
  }

  return <>{children}</>;
}

function ForbiddenPanel() {
  return (
    <div className="mx-auto flex max-w-lg flex-col items-center gap-4 px-5 py-24 text-center">
      <div className="rounded-full bg-orange-500/12 p-4">
        <Lock aria-hidden="true" className="size-8 text-orange-600" strokeWidth={1.5} />
      </div>
      <h1 className="text-h2 font-display font-bold">Não tens acesso a esta página</h1>
      <p className="text-body text-muted">
        A tua conta não tem o perfil necessário para ver este conteúdo. Se achas que isto é um erro, contacta-nos.
      </p>
      <Link
        to="/"
        className="inline-flex h-11 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-5 font-medium text-accent-fg"
      >
        Voltar à página inicial
      </Link>
    </div>
  );
}
