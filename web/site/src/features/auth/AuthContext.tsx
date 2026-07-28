import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { USE_MOCKS } from '../../services';
import { mockCurrentUser } from '../../services/mock/currentUser';
import { mockDb } from '../../services/mock/db';
import { beginLogin, endSession, fetchSession } from './bffClient';
import { DEMO_PROFILES, type DemoProfileKey } from './demoProfiles';
import type { AuthContextValue, AuthStatus, Role, SessionUser } from './types';

// eslint-disable-next-line react-refresh/only-export-components -- contexto e provider vivem juntos de propósito; só afeta HMR em dev.
export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/**
 * `loginAsDemoProfile` só é usado pelo ecrã `/entrar` em modo mock — em modo
 * real esse ecrã nunca chama isto (redireciona de imediato para o BFF). Fica
 * fora de `AuthContextValue` (que é igual nos dois modos) para não vazar um
 * conceito "demo" para o resto da app.
 */
interface MockAuthContextValue extends AuthContextValue {
  loginAsDemoProfile: (key: DemoProfileKey, returnTo: string) => Promise<void>;
}

// eslint-disable-next-line react-refresh/only-export-components -- contexto e provider vivem juntos de propósito; só afeta HMR em dev.
export const MockAuthContext = createContext<MockAuthContextValue | undefined>(undefined);

const MOCK_LATENCY_MS = 800;

function MockAuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('unauthenticated');
  const [user, setUser] = useState<SessionUser | undefined>(undefined);

  // Sessão só em memória (§9) — recarregar a página termina a sessão mock,
  // de propósito: nunca se persiste em localStorage/sessionStorage.
  const login = useCallback((returnTo: string) => {
    window.location.assign(`/entrar?returnTo=${encodeURIComponent(returnTo)}`);
  }, []);

  const loginAsDemoProfile = useCallback(async (key: DemoProfileKey) => {
    setStatus('loading');
    await new Promise((resolve) => setTimeout(resolve, MOCK_LATENCY_MS));
    const profile = DEMO_PROFILES.find((candidate) => candidate.key === key) ?? DEMO_PROFILES[0];
    mockCurrentUser.set({ id: profile.user.id, roles: profile.user.roles });
    if (profile.subscriptionActive !== undefined) {
      mockDb.setProviderSubscriptionActive(profile.subscriptionActive);
    }
    setUser(profile.user);
    setStatus('authenticated');
  }, []);

  const logout = useCallback((): Promise<void> => {
    mockCurrentUser.set(undefined);
    setUser(undefined);
    setStatus('unauthenticated');
    return Promise.resolve();
  }, []);

  const hasRole = useCallback((role: Role) => user?.roles.includes(role) ?? false, [user]);

  const value = useMemo<MockAuthContextValue>(
    () => ({ status, user, login, loginAsDemoProfile, logout, hasRole }),
    [status, user, login, loginAsDemoProfile, logout, hasRole],
  );

  return (
    <AuthContext.Provider value={value}>
      <MockAuthContext.Provider value={value}>{children}</MockAuthContext.Provider>
    </AuthContext.Provider>
  );
}

function RealAuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<SessionUser | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    fetchSession()
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

  const login = useCallback((returnTo: string) => beginLogin(returnTo), []);

  const logout = useCallback(async () => {
    const logoutUrl = await endSession();
    setUser(undefined);
    setStatus('unauthenticated');
    // Termina também a sessão SSO no Keycloak (CLAUDE.md §4) — navegação de
    // topo, não `fetch`.
    window.location.href = logoutUrl;
  }, []);

  const hasRole = useCallback((role: Role) => user?.roles.includes(role) ?? false, [user]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout, hasRole }),
    [status, user, login, logout, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * Ponto único de seleção mock ↔ real (§8.4, aplicado à autenticação). Em
 * ambos os modos, `localStorage`/`sessionStorage` nunca guardam tokens
 * (CLAUDE.md §4) — o modo real usa cookie `HttpOnly` do BFF; o modo mock usa
 * só estado React em memória.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  return USE_MOCKS ? <MockAuthProvider>{children}</MockAuthProvider> : <RealAuthProvider>{children}</RealAuthProvider>;
}
