import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { tokenStorage } from '../api/tokenStorage';
import { decodeJwt } from '../api/jwt';
import { authApi, type RegisterInput } from '../api/endpoints';
import type { UserRole } from '../api/types';

export interface AuthUser {
  userId: string;
  email: string | undefined;
  role: UserRole;
}

interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function userFromToken(): AuthUser | null {
  const token = tokenStorage.getAccess();
  if (!token) return null;
  const claims = decodeJwt(token);
  if (!claims) return null;
  return { userId: claims.sub, email: claims.email, role: claims.role };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(userFromToken);

  // Keep React state in sync with the token store (refresh rotation, logout in another tab).
  useEffect(() => tokenStorage.subscribe(() => setUser(userFromToken())), []);

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await authApi.login(email, password);
    tokenStorage.set(tokens.accessToken, tokens.refreshToken);
  }, []);

  const register = useCallback(async (input: RegisterInput) => {
    await authApi.register(input);
    await login(input.email, input.password);
  }, [login]);

  const logout = useCallback(() => tokenStorage.clear(), []);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    isAuthenticated: user !== null,
    isAdmin: user?.role === 'ADMIN',
    login,
    register,
    logout,
  }), [user, login, register, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
