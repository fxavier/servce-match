import type { SessionUser } from './types';

/** Destino pós-login/registo quando não há `returnTo` explícito — por papel. */
export function defaultDashboardFor(user: SessionUser): string {
  return user.roles.includes('PROVIDER') ? '/pro' : '/painel';
}
