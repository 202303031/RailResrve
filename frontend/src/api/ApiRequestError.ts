import type { ApiError, ApiFieldError } from './types';

/**
 * A failed API call, normalised so the UI can rely on a machine-readable `code` and a human
 * `message` regardless of transport (a 4xx/5xx envelope, or a network error with no response).
 */
export class ApiRequestError extends Error {
  readonly code: string;
  readonly fieldErrors: ApiFieldError[];
  readonly status: number | undefined;

  constructor(code: string, message: string, fieldErrors: ApiFieldError[] = [], status?: number) {
    super(message);
    this.name = 'ApiRequestError';
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.status = status;
  }

  static fromError(error: ApiError, status?: number): ApiRequestError {
    return new ApiRequestError(error.code, error.message, error.fieldErrors ?? [], status);
  }
}
