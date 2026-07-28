import { useContext } from 'react';
import { AuthContext, MockAuthContext } from './AuthContext';
import type { AuthContextValue } from './types';

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}

/** Só para o ecrã `/entrar` em modo mock — ver comentário em AuthContext.tsx. */
export function useMockAuth() {
  const ctx = useContext(MockAuthContext);
  if (!ctx) throw new Error('useMockAuth só está disponível dentro de MockAuthProvider (VITE_USE_MOCKS=true).');
  return ctx;
}
