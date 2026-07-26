import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { tokenStorage } from './tokenStorage';
import { ApiRequestError } from './ApiRequestError';
import type { ApiResponse, TokenResponse } from './types';

const API_ROOT = `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/v1`;

export const http: AxiosInstance = axios.create({
  baseURL: API_ROOT,
  headers: { 'Content-Type': 'application/json' },
});

// --- Request: attach the access token -------------------------------------------------------
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStorage.getAccess();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// --- Response: transparently refresh once on a 401 -----------------------------------------
// A single in-flight refresh is shared by all requests that 401 at once, so a burst of expired
// calls triggers exactly one refresh, then all retry with the new token.
let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStorage.getRefresh();
  if (!refreshToken) {
    throw new ApiRequestError('UNAUTHENTICATED', 'Session expired');
  }
  // Bare axios (not `http`) so this call skips the interceptors and can't recurse.
  const response = await axios.post<ApiResponse<TokenResponse>>(
    `${API_ROOT}/auth/refresh`,
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' } },
  );
  const tokens = response.data.data;
  if (!tokens) {
    throw new ApiRequestError('UNAUTHENTICATED', 'Could not refresh session');
  }
  tokenStorage.set(tokens.accessToken, tokens.refreshToken);
  return tokens.accessToken;
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const status = error.response?.status;
    const isAuthCall = original?.url?.includes('/auth/');

    if (status === 401 && original && !original._retried && !isAuthCall && tokenStorage.getRefresh()) {
      original._retried = true;
      try {
        refreshPromise ??= refreshAccessToken().finally(() => {
          refreshPromise = null;
        });
        const newToken = await refreshPromise;
        original.headers.Authorization = `Bearer ${newToken}`;
        return http(original);
      } catch {
        tokenStorage.clear();
      }
    }
    return Promise.reject(toApiRequestError(error));
  },
);

function toApiRequestError(error: AxiosError<ApiResponse<unknown>>): ApiRequestError {
  const envelope = error.response?.data;
  if (envelope?.error) {
    return ApiRequestError.fromError(envelope.error, error.response?.status);
  }
  if (error.response) {
    return new ApiRequestError('HTTP_ERROR', `Request failed (${error.response.status})`, [], error.response.status);
  }
  return new ApiRequestError('NETWORK_ERROR', 'Could not reach the server. Please check your connection.');
}

/** Unwrap the ApiResponse envelope to its data, throwing an ApiRequestError on failure. */
function unwrap<T>(response: AxiosResponse<ApiResponse<T>>): T {
  const body = response.data;
  if (!body.success || body.data === null) {
    throw ApiRequestError.fromError(
      body.error ?? { code: 'UNKNOWN', message: 'Unexpected empty response', fieldErrors: [] },
      response.status,
    );
  }
  return body.data;
}

export const api = {
  async get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    return unwrap(await http.get<ApiResponse<T>>(url, { params }));
  },
  async post<T>(url: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
    return unwrap(await http.post<ApiResponse<T>>(url, body, { headers }));
  },
  async delete<T>(url: string): Promise<T> {
    return unwrap(await http.delete<ApiResponse<T>>(url));
  },
};
