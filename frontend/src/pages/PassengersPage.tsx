import { FormEvent, useMemo, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { bookingsApi } from '../api/endpoints';
import { ApiRequestError } from '../api/ApiRequestError';
import type { Gender, PassengerRequest } from '../api/types';
import { TRAVEL_CLASS_LABELS } from '../api/types';
import { Button, Card, Input, Select } from '../components/ui';
import { useToast } from '../components/Toast';
import type { PassengersNavState } from './bookingFlow';

export function PassengersPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const state = useLocation().state as PassengersNavState | null;

  // A stable idempotency key for this hold attempt, so a retry never books twice.
  const idempotencyKey = useMemo(() => (crypto.randomUUID ? crypto.randomUUID() : String(Date.now())), []);

  const [passengers, setPassengers] = useState<PassengerRequest[]>(
    () => (state?.seats ?? []).map(() => ({ name: '', age: 30, gender: 'MALE' as Gender })),
  );
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!state || state.seats.length === 0) {
    return <Navigate to="/" replace />;
  }

  function update(index: number, patch: Partial<PassengerRequest>) {
    setPassengers((current) => current.map((p, i) => (i === index ? { ...p, ...patch } : p)));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!state) return;
    setError(null);
    setSubmitting(true);
    try {
      const hold = await bookingsApi.hold({
        scheduleId: state.scheduleId,
        coachId: state.coachId,
        seatIds: state.seats.map((s) => s.seatId),
        passengers,
      }, idempotencyKey);
      navigate('/book/payment', {
        replace: true,
        state: {
          trainName: state.trainName,
          trainNumber: state.trainNumber,
          journeyDate: state.journeyDate,
          coachCode: state.coachCode,
          travelClass: state.travelClass,
          holdId: hold.holdId,
          expiresAt: hold.expiresAt,
          totalFare: hold.totalFare,
          passengerCount: passengers.length,
        },
      });
    } catch (err) {
      const message = err instanceof ApiRequestError ? err.message : 'Could not hold the seats';
      setError(message);
      notify(message, 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <button onClick={() => navigate(-1)} className="mb-2 text-sm text-slate-500 hover:text-slate-700">← Back to seats</button>
      <h1 className="text-2xl font-semibold text-slate-900">Passenger details</h1>
      <p className="mt-1 text-sm text-slate-500">
        Coach {state.coachCode} · {TRAVEL_CLASS_LABELS[state.travelClass]} · {state.seats.length} seat(s)
      </p>

      <form onSubmit={onSubmit} className="mt-6 space-y-4" aria-label="Passenger details">
        {state.seats.map((seat, index) => (
          <Card key={seat.seatId}>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-sm font-semibold text-slate-800">Passenger {index + 1}</h2>
              <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">Seat {seat.seatNumber}</span>
            </div>
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="sm:col-span-1">
                <Input label="Name" required value={passengers[index].name}
                  onChange={(e) => update(index, { name: e.target.value })} />
              </div>
              <Input label="Age" type="number" min={1} max={119} required value={passengers[index].age}
                onChange={(e) => update(index, { age: Number(e.target.value) })} />
              <Select label="Gender" value={passengers[index].gender}
                onChange={(e) => update(index, { gender: e.target.value as Gender })}>
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
                <option value="OTHER">Other</option>
              </Select>
            </div>
          </Card>
        ))}
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end">
          <Button type="submit" loading={submitting}>Hold seats &amp; continue</Button>
        </div>
      </form>
    </div>
  );
}
