import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { trainsApi } from '../api/endpoints';
import { ApiRequestError } from '../api/ApiRequestError';
import type { CoachAvailability, SeatMapResponse, SeatView } from '../api/types';
import { TRAVEL_CLASS_LABELS } from '../api/types';
import { Button, Card, EmptyState, Skeleton } from '../components/ui';
import { classNames } from '../lib/format';
import { MAX_SEATS_PER_BOOKING, type TrainInfo } from './bookingFlow';

export function SeatSelectionPage() {
  const { scheduleId: scheduleIdParam } = useParams();
  const scheduleId = Number(scheduleIdParam);
  const navigate = useNavigate();
  const trainInfo = (useLocation().state as TrainInfo | null) ?? {};

  const [coaches, setCoaches] = useState<CoachAvailability[] | null>(null);
  const [coachId, setCoachId] = useState<number | null>(null);
  const [seatMap, setSeatMap] = useState<SeatMapResponse | null>(null);
  const [selected, setSelected] = useState<Record<number, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [loadingSeats, setLoadingSeats] = useState(false);

  useEffect(() => {
    let active = true;
    trainsApi.availability(scheduleId)
      .then((res) => {
        if (!active) return;
        setCoaches(res.coaches);
        const firstOpen = res.coaches.find((c) => c.availableCount > 0) ?? res.coaches[0];
        setCoachId(firstOpen?.coachId ?? null);
      })
      .catch((err) => active && setError(err instanceof ApiRequestError ? err.message : 'Could not load coaches'));
    return () => { active = false; };
  }, [scheduleId]);

  useEffect(() => {
    if (coachId == null) return;
    let active = true;
    setLoadingSeats(true);
    setSelected({});
    trainsApi.seatMap(scheduleId, coachId)
      .then((res) => active && setSeatMap(res))
      .catch((err) => active && setError(err instanceof ApiRequestError ? err.message : 'Could not load seats'))
      .finally(() => active && setLoadingSeats(false));
    return () => { active = false; };
  }, [scheduleId, coachId]);

  const selectedIds = Object.keys(selected).map(Number);

  function toggleSeat(seat: SeatView) {
    if (!seat.available) return;
    setSelected((current) => {
      const next = { ...current };
      if (next[seat.seatId]) {
        delete next[seat.seatId];
      } else if (selectedIds.length < MAX_SEATS_PER_BOOKING) {
        next[seat.seatId] = seat.seatNumber;
      }
      return next;
    });
  }

  function onContinue() {
    if (!seatMap || selectedIds.length === 0) return;
    navigate(`/book/${scheduleId}/passengers`, {
      state: {
        ...trainInfo,
        scheduleId,
        coachId: seatMap.coachId,
        coachCode: seatMap.coachCode,
        travelClass: seatMap.travelClass,
        seats: selectedIds.map((id) => ({ seatId: id, seatNumber: selected[id] })),
      },
    });
  }

  if (error) return <EmptyState title="Unavailable" description={error} />;
  if (!coaches) return <div className="space-y-3"><Skeleton className="h-10 w-64" /><Skeleton className="h-64 w-full" /></div>;

  return (
    <div>
      <button onClick={() => navigate(-1)} className="mb-2 text-sm text-slate-500 hover:text-slate-700">← Back</button>
      <h1 className="text-2xl font-semibold text-slate-900">
        {trainInfo.trainName ?? 'Select seats'}{trainInfo.trainNumber && <span className="ml-2 text-base text-slate-500">#{trainInfo.trainNumber}</span>}
      </h1>

      <div className="mt-4 flex flex-wrap gap-2">
        {coaches.map((coach) => (
          <button
            key={coach.coachId}
            onClick={() => setCoachId(coach.coachId)}
            className={classNames(
              'rounded-md px-3 py-1.5 text-sm ring-1 transition',
              coach.coachId === coachId ? 'bg-brand-600 text-white ring-brand-600' : 'bg-white text-slate-700 ring-slate-300 hover:bg-slate-50',
            )}
          >
            {coach.coachCode} · {TRAVEL_CLASS_LABELS[coach.travelClass]}
            <span className="ml-1 text-xs opacity-80">({coach.availableCount})</span>
          </button>
        ))}
      </div>

      <Card className="mt-4">
        {loadingSeats || !seatMap ? (
          <Skeleton className="h-56 w-full" />
        ) : (
          <>
            <div className="mb-4 flex items-center gap-4 text-xs text-slate-500">
              <span className="flex items-center gap-1"><span className="h-3 w-3 rounded bg-white ring-1 ring-slate-300" /> Available</span>
              <span className="flex items-center gap-1"><span className="h-3 w-3 rounded bg-brand-600" /> Selected</span>
              <span className="flex items-center gap-1"><span className="h-3 w-3 rounded bg-slate-200" /> Taken</span>
            </div>
            <div className="grid grid-cols-4 gap-2 sm:grid-cols-8" role="group" aria-label="Seats">
              {seatMap.seats.map((seat) => {
                const isSelected = Boolean(selected[seat.seatId]);
                return (
                  <button
                    key={seat.seatId}
                    onClick={() => toggleSeat(seat)}
                    disabled={!seat.available}
                    aria-pressed={isSelected}
                    aria-label={`Seat ${seat.seatNumber}${seat.available ? '' : ' (taken)'}`}
                    className={classNames(
                      'aspect-square rounded-md text-xs font-medium ring-1 transition',
                      !seat.available && 'cursor-not-allowed bg-slate-200 text-slate-400 ring-slate-200',
                      seat.available && !isSelected && 'bg-white text-slate-700 ring-slate-300 hover:ring-brand-400',
                      isSelected && 'bg-brand-600 text-white ring-brand-600',
                    )}
                  >
                    {seat.seatNumber}
                  </button>
                );
              })}
            </div>
          </>
        )}
      </Card>

      <div className="mt-6 flex flex-col items-center justify-between gap-3 sm:flex-row">
        <p className="text-sm text-slate-600">
          {selectedIds.length > 0
            ? `Selected ${selectedIds.length} seat${selectedIds.length > 1 ? 's' : ''}: ${selectedIds.map((id) => selected[id]).join(', ')}`
            : `Pick up to ${MAX_SEATS_PER_BOOKING} seats`}
        </p>
        <Button onClick={onContinue} disabled={selectedIds.length === 0}>Continue</Button>
      </div>
    </div>
  );
}
