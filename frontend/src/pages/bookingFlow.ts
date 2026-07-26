import type { TravelClass } from '../api/types';

/** Train display info threaded through the booking flow via router navigation state. */
export interface TrainInfo {
  trainName?: string;
  trainNumber?: string;
  journeyDate?: string;
}

export interface SelectedSeat {
  seatId: number;
  seatNumber: string;
}

/** State handed from seat selection to the passengers page. */
export interface PassengersNavState extends TrainInfo {
  scheduleId: number;
  coachId: number;
  coachCode: string;
  travelClass: TravelClass;
  seats: SelectedSeat[];
}

/** State handed from the passengers page (after the hold is created) to the payment page. */
export interface PaymentNavState extends TrainInfo {
  holdId: number;
  expiresAt: string;
  totalFare: number;
  coachCode: string;
  travelClass: TravelClass;
  passengerCount: number;
}

export const MAX_SEATS_PER_BOOKING = 6;
