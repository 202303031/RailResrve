import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SearchPage } from './SearchPage';
import type { PageResponse, TrainSearchResult } from '../api/types';

const { searchMock } = vi.hoisted(() => ({ searchMock: vi.fn() }));
vi.mock('../api/endpoints', () => ({ trainsApi: { search: searchMock } }));

function page(content: TrainSearchResult[]): PageResponse<TrainSearchResult> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1, last: true };
}

const SAMPLE: TrainSearchResult = {
  scheduleId: 1, trainNumber: '12951', trainName: 'Rajdhani Express', trainType: 'RAJDHANI',
  journeyDate: '2026-08-01', departureTime: '16:35:00', arrivalTime: '08:15:00',
  distanceKm: 1384, durationMinutes: 940,
  availability: [{ travelClass: '3A', availableSeats: 12 }],
};

describe('SearchPage', () => {
  beforeEach(() => searchMock.mockReset());

  it('renders matching trains after a search', async () => {
    searchMock.mockResolvedValueOnce(page([SAMPLE]));
    render(<MemoryRouter><SearchPage /></MemoryRouter>);

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    expect(await screen.findByText('Rajdhani Express')).toBeInTheDocument();
    expect(screen.getByText(/12 left/)).toBeInTheDocument();
    expect(searchMock).toHaveBeenCalledWith('NDLS', 'BCT', expect.any(String));
  });

  it('shows an empty state when no trains match', async () => {
    searchMock.mockResolvedValueOnce(page([]));
    render(<MemoryRouter><SearchPage /></MemoryRouter>);

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => expect(screen.getByText(/no trains found/i)).toBeInTheDocument());
  });
});
