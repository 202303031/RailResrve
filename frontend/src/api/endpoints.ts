import { api } from './client';
import type {
  AvailabilityResponse,
  BookingDetailResponse,
  BookingSummary,
  CancelResponse,
  ConfirmResponse,
  HoldRequest,
  HoldResponse,
  PageResponse,
  RegisterResponse,
  SeatMapResponse,
  TokenResponse,
  TrainSearchResult,
  TravelClass,
} from './types';

export interface RegisterInput {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
}

export const authApi = {
  register: (input: RegisterInput) => api.post<RegisterResponse>('/auth/register', input),
  login: (email: string, password: string) => api.post<TokenResponse>('/auth/login', { email, password }),
};

export const trainsApi = {
  search: (from: string, to: string, date: string, page = 0, size = 10) =>
    api.get<PageResponse<TrainSearchResult>>('/trains/search', { from, to, date, page, size }),
  availability: (scheduleId: number, travelClass?: TravelClass) =>
    api.get<AvailabilityResponse>(`/schedules/${scheduleId}/availability`, travelClass ? { travelClass } : undefined),
  seatMap: (scheduleId: number, coachId: number) =>
    api.get<SeatMapResponse>(`/schedules/${scheduleId}/coaches/${coachId}/seats`),
};

export const bookingsApi = {
  hold: (request: HoldRequest, idempotencyKey?: string) =>
    api.post<HoldResponse>('/bookings/hold', request, idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined),
  confirm: (holdId: number, paymentToken: string) =>
    api.post<ConfirmResponse>('/bookings/confirm', { holdId, paymentToken }),
  list: (page = 0, size = 10) =>
    api.get<PageResponse<BookingSummary>>('/bookings', { page, size }),
  get: (pnr: string) => api.get<BookingDetailResponse>(`/bookings/${pnr}`),
  cancel: (pnr: string) => api.delete<CancelResponse>(`/bookings/${pnr}`),
};

export const adminApi = {
  allBookings: (page = 0, size = 20) =>
    api.get<PageResponse<BookingSummary>>('/admin/bookings', { page, size }),
};
