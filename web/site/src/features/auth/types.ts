export type Role = 'CUSTOMER' | 'PROVIDER' | 'ADMIN';

export interface SessionUser {
  id: string;
  displayName: string;
  email: string;
  roles: Role[];
  avatarUrl?: string | null;
}

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

export interface AuthContextValue {
  status: AuthStatus;
  user: SessionUser | undefined;
  login: (returnTo: string) => void;
  logout: () => Promise<void>;
  hasRole: (role: Role) => boolean;
}
