import type { UserRole } from './types';

export interface JwtClaims {
  sub: string;
  role: UserRole;
  email?: string;
  exp?: number;
}

/** Decode a JWT payload (no signature check — that's the server's job; this is only for UI state). */
export function decodeJwt(token: string): JwtClaims | null {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json) as JwtClaims;
  } catch {
    return null;
  }
}
