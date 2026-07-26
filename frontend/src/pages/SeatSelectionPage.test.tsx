import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SeatSelectionPage } from './SeatSelectionPage';
import type { AvailabilityResponse, SeatMapResponse } from '../api/types';

const { availabilityMock, seatMapMock } = vi.hoisted(() => ({
  availabilityMock: vi.fn(),
  seatMapMock: vi.fn(),
}));
vi.mock('../api/endpoints', () => ({
  trainsApi: { availability: availabilityMock, seatMap: seatMapMock },
}));

const AVAILABILITY: AvailabilityResponse = {
  scheduleId: 1, journeyDate: '2026-08-01', trainNumber: '12951', trainName: 'Rajdhani',
  coaches: [{ coachId: 10, coachCode: 'B1', travelClass: '3A', availableCount: 1, totalSeats: 2 }],
};
const SEAT_MAP: SeatMapResponse = {
  scheduleId: 1, coachId: 10, coachCode: 'B1', travelClass: '3A',
  seats: [
    { seatId: 100, seatNumber: '1', berthType: 'LOWER', available: true },
    { seatId: 101, seatNumber: '2', berthType: 'UPPER', available: false },
  ],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/book/1/seats']}>
      <Routes>
        <Route path="/book/:scheduleId/seats" element={<SeatSelectionPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('SeatSelectionPage', () => {
  beforeEach(() => {
    availabilityMock.mockReset().mockResolvedValue(AVAILABILITY);
    seatMapMock.mockReset().mockResolvedValue(SEAT_MAP);
  });

  it('renders the seat grid and toggles an available seat', async () => {
    renderPage();

    const seat = await screen.findByRole('button', { name: 'Seat 1' });
    const takenSeat = screen.getByRole('button', { name: 'Seat 2 (taken)' });
    expect(takenSeat).toBeDisabled();

    await userEvent.click(seat);

    expect(seat).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText(/Selected 1 seat: 1/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /continue/i })).toBeEnabled();
  });
});
