import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { beginLogin, endSession, fetchSession, type SessionUser } from './bffClient';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

export interface AuthContextValue {
  status: AuthStatus;
  user: SessionUser | undefined;
  login: (returnTo: string) => void;
  logout: () => Promise<void>;
  hasRole: (role: string) => boolean;
}

// eslint-disable-next-line react-refresh/only-export-components -- contexto e provider vivem juntos de propósito; só afeta HMR em dev.
export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<SessionUser | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    fetchSession()
      .then((session) => {
        if (cancelled) return;
        if (session.authenticated) {
          setUser(session.user);
          setStatus('authenticated');
        } else {
          setUser(undefined);
          setStatus('unauthenticated');
        }
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

  const hasRole = useCallback((role: string) => user?.roles.includes(role) ?? false, [user]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout, hasRole }),
    [status, user, login, logout, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
