// TypeScript mirror of the backend API contract (com.railreserve.*.web.dto).
// Kept in one place so a contract change surfaces as a compile error across the app.

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiError {
  code: string;
  message: string;
  fieldErrors: ApiFieldError[];
}

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: ApiError | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

// Travel-class external codes (TravelClass.getCode()).
export type TravelClass = 'SL' | '3A' | '2A' | '1A' | 'CC' | '2S' | 'EC';

export const TRAVEL_CLASS_LABELS: Record<TravelClass, string> = {
  SL: 'Sleeper',
  '3A': 'AC 3 Tier',
  '2A': 'AC 2 Tier',
  '1A': 'AC 1 Tier',
  CC: 'Chair Car',
  '2S': 'Second Sitting',
  EC: 'Executive Chair',
};

export type BookingStatus =
  | 'PENDING' | 'HELD' | 'CONFIRMED' | 'WAITLISTED' | 'RAC' | 'CANCELLED' | 'EXPIRED';

export type Gender = 'MALE' | 'FEMALE' | 'OTHER';
export type UserRole = 'USER' | 'ADMIN';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface RegisterResponse {
  userId: number;
  email: string;
  fullName: string;
  role: UserRole;
}

export interface ClassAvailability {
  travelClass: TravelClass;
  availableSeats: number;
}

export interface TrainSearchResult {
  scheduleId: number;
  trainNumber: string;
  trainName: string;
  trainType: string;
  journeyDate: string;
  departureTime: string | null;
  arrivalTime: string | null;
  distanceKm: number;
  durationMinutes: number;
  availability: ClassAvailability[];
}

export type BerthType =
  | 'LOWER' | 'MIDDLE' | 'UPPER' | 'SIDE_LOWER' | 'SIDE_UPPER' | 'WINDOW' | 'AISLE' | 'NONE';

export interface SeatView {
  seatId: number;
  seatNumber: string;
  berthType: BerthType;
  available: boolean;
}

export interface SeatMapResponse {
  scheduleId: number;
  coachId: number;
  coachCode: string;
  travelClass: TravelClass;
  seats: SeatView[];
}

export interface CoachAvailability {
  coachId: number;
  coachCode: string;
  travelClass: TravelClass;
  availableCount: number;
  totalSeats: number;
}

export interface AvailabilityResponse {
  scheduleId: number;
  journeyDate: string;
  trainNumber: string;
  trainName: string;
  coaches: CoachAvailability[];
}

export interface PassengerRequest {
  name: string;
  age: number;
  gender: Gender;
}

export interface HoldRequest {
  scheduleId: number;
  coachId: number;
  seatIds: number[];
  passengers: PassengerRequest[];
}

export interface HoldResponse {
  holdId: number;
  expiresAt: string;
  totalFare: number;
}

export interface ConfirmResponse {
  pnr: string;
  status: BookingStatus;
}

export interface BookingSummary {
  pnr: string;
  status: BookingStatus;
  journeyDate: string;
  trainNumber: string;
  trainName: string;
  totalFare: number;
  passengerCount: number;
  createdAt: string;
}

export interface PassengerView {
  name: string;
  age: number;
  gender: Gender;
  status: string;
  seatNumber: string | null;
  coachCode: string | null;
}

export interface BookingDetailResponse {
  pnr: string;
  status: BookingStatus;
  journeyDate: string;
  trainNumber: string;
  trainName: string;
  totalFare: number;
  waitlistPosition: number | null;
  createdAt: string;
  passengers: PassengerView[];
}

export interface CancelResponse {
  pnr: string;
  status: BookingStatus;
  refundAmount: number;
}
