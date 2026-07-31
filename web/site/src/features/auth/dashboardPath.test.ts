import { describe, expect, it } from 'vitest';
import { defaultDashboardFor } from './dashboardPath';
import type { SessionUser } from './types';

function user(roles: SessionUser['roles']): SessionUser {
  return { id: 'u-1', displayName: 'Utilizador', email: 'u@example.com', roles };
}

describe('defaultDashboardFor', () => {
  it('vai para /pro quando o utilizador é PROVIDER, mesmo com outros papéis', () => {
    expect(defaultDashboardFor(user(['PROVIDER']))).toBe('/pro');
    expect(defaultDashboardFor(user(['ADMIN', 'PROVIDER']))).toBe('/pro');
  });

  it('vai para /admin quando o utilizador é só ADMIN (defeito C1 — sem isto ficaria preso em /painel, sem acesso)', () => {
    expect(defaultDashboardFor(user(['ADMIN']))).toBe('/admin');
  });

  it('vai para /painel por omissão (CUSTOMER, ou sem papel reconhecido)', () => {
    expect(defaultDashboardFor(user(['CUSTOMER']))).toBe('/painel');
    expect(defaultDashboardFor(user([]))).toBe('/painel');
  });
});
