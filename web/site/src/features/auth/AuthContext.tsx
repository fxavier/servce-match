import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { throwProblem, toProblem } from '../../lib/problem';
import * as bffClient from './bffClient';
import type { AuthContextValue, AuthStatus, LoginCredentials, RegisterInput, Role, SessionUser } from './types';

// eslint-disable-next-line react-refresh/only-export-components -- contexto e provider vivem juntos de propósito; só afeta HMR em dev.
export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/**
 * Sessão sem IdP visível (ADR-0012): formulários próprios falam com o BFF
 * (`/auth/login`, `/auth/register`, `/auth/logout`); `GET /auth/me`
 * continua a ser a única fonte de verdade sobre quem está autenticado — o
 * estado local (`user`) é sempre derivado dele, nunca assumido a partir do
 * corpo de uma resposta de login/registo. Nenhum token, password ou perfil
 * sensível chega a `localStorage`/`sessionStorage` (CLAUDE.md §4).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<SessionUser | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    bffClient
      .fetchSession()
      .then((sessionUser) => {
        if (cancelled) return;
        setUser(sessionUser);
        setStatus(sessionUser ? 'authenticated' : 'unauthenticated');
      })
      .catch(() => {
        if (!cancelled) setStatus('unauthenticated');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (credentials: LoginCredentials): Promise<SessionUser> => {
    await bffClient.login(credentials);
    const sessionUser = await bffClient.fetchSession();
    if (!sessionUser) {
      // O BFF respondeu com sucesso mas a sessão não ficou visível em
      // `/auth/me` — não deveria acontecer; trata-se como falha explícita
      // em vez de fingir que a sessão existe.
      throwProblem(toProblem(undefined, 'Não foi possível confirmar a sessão. Tenta novamente.'));
    }
    setUser(sessionUser);
    setStatus('authenticated');
    return sessionUser;
  }, []);

  const register = useCallback(async (input: RegisterInput): Promise<SessionUser | undefined> => {
    await bffClient.register(input);
    const sessionUser = await bffClient.fetchSession();
    setUser(sessionUser);
    setStatus(sessionUser ? 'authenticated' : 'unauthenticated');
    return sessionUser;
  }, []);

  const logout = useCallback(async () => {
    await bffClient.logout();
    setUser(undefined);
    setStatus('unauthenticated');
  }, []);

  const hasRole = useCallback((role: Role) => user?.roles.includes(role) ?? false, [user]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, register, logout, hasRole }),
    [status, user, login, register, logout, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
