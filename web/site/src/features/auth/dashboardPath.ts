import type { SessionUser } from './types';

/**
 * Destino pós-login/registo quando não há `returnTo` explícito — por papel.
 * `PROVIDER` tem prioridade sobre `ADMIN` (um prestador que também seja
 * administrador continua a querer o painel de prestador por omissão);
 * `ADMIN` sem `CUSTOMER`/`PROVIDER` vai para `/admin` — sem este caso, uma
 * conta só-administradora seria redirecionada para `/painel`, que exige
 * `CUSTOMER` e mostra "Não tens acesso a esta página" (ver
 * `routes/ProtectedRoute.tsx`).
 */
export function defaultDashboardFor(user: SessionUser): string {
  if (user.roles.includes('PROVIDER')) return '/pro';
  if (user.roles.includes('ADMIN')) return '/admin';
  return '/painel';
}
