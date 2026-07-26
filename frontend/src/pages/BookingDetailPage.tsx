import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { bookingsApi } from '../api/endpoints';
import { ApiRequestError } from '../api/ApiRequestError';
import type { BookingDetailResponse } from '../api/types';
import { Card, EmptyState, Skeleton, StatusBadge } from '../components/ui';
import { formatCurrency, formatDate } from '../lib/format';

export function BookingDetailPage() {
  const { pnr } = useParams();
  const [booking, setBooking] = useState<BookingDetailResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!pnr) return;
    bookingsApi.get(pnr)
      .then(setBooking)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'Could not load booking'));
  }, [pnr]);

  if (error) {
    return <EmptyState title="Booking not found" description={error}
      action={<Link to="/bookings" className="text-sm font-medium text-brand-700 hover:underline">Back to my bookings</Link>} />;
  }
  if (!booking) return <Skeleton className="h-64 w-full" />;

  return (
    <div className="mx-auto max-w-2xl">
      <Link to="/bookings" className="text-sm text-slate-500 hover:text-slate-700">← My bookings</Link>
      <div className="mt-2 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">{booking.trainName}</h1>
        <StatusBadge status={booking.status} />
      </div>
      <p className="mt-1 text-sm text-slate-500">
        PNR {booking.pnr} · #{booking.trainNumber} · {formatDate(booking.journeyDate)}
      </p>
      {booking.waitlistPosition != null && (
        <p className="mt-2 inline-block rounded bg-sky-50 px-2 py-1 text-sm text-sky-700">
          Waitlist position {booking.waitlistPosition}
        </p>
      )}

      <Card className="mt-6">
        <h2 className="mb-3 text-sm font-semibold text-slate-800">Passengers</h2>
        <ul className="divide-y divide-slate-100">
          {booking.passengers.map((passenger, i) => (
            <li key={i} className="flex items-center justify-between py-2 text-sm">
              <span className="text-slate-800">{passenger.name} <span className="text-slate-400">· {passenger.age} · {passenger.gender}</span></span>
              <span className="text-slate-600">
                {passenger.seatNumber ? `${passenger.coachCode}/${passenger.seatNumber}` : passenger.status}
              </span>
            </li>
          ))}
        </ul>
        <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-4">
          <span className="text-sm text-slate-600">Total fare</span>
          <span className="text-lg font-semibold text-slate-900">{formatCurrency(booking.totalFare)}</span>
        </div>
      </Card>
    </div>
  );
}
