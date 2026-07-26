// Single source of truth for the JWT pair. Both the axios client and the auth context read/write
// here, and the context subscribes so React state stays in sync (e.g. when a 401 refresh rotates
// the tokens, or another tab logs out).

const ACCESS_KEY = 'rr_access_token';
const REFRESH_KEY = 'rr_refresh_token';

type Listener = () => void;
const listeners = new Set<Listener>();

function notify() {
  listeners.forEach((fn) => fn());
}

export const tokenStorage = {
  getAccess(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  },
  getRefresh(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  },
  set(accessToken: string, refreshToken: string): void {
    localStorage.setItem(ACCESS_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
    notify();
  },
  clear(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    notify();
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
