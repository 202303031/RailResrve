import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { bookingsApi } from '../api/endpoints';
import { ApiRequestError } from '../api/ApiRequestError';
import type { BookingSummary } from '../api/types';
import { Button, Card, EmptyState, Skeleton, StatusBadge } from '../components/ui';
import { useToast } from '../components/Toast';
import { formatCurrency, formatDate } from '../lib/format';

const CANCELLABLE = new Set(['CONFIRMED', 'HELD', 'WAITLISTED', 'RAC']);

export function MyBookingsPage() {
  const { notify } = useToast();
  const [bookings, setBookings] = useState<BookingSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<string | null>(null);

  function load() {
    setError(null);
    bookingsApi.list()
      .then((page) => setBookings(page.content))
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'Could not load bookings'));
  }

  useEffect(load, []);

  async function onCancel(pnr: string) {
    if (!window.confirm(`Cancel booking ${pnr}? Any eligible refund will be issued.`)) return;
    setCancelling(pnr);
    try {
      const result = await bookingsApi.cancel(pnr);
      notify(`Booking ${pnr} cancelled. Refund: ${formatCurrency(result.refundAmount)}`, 'success');
      load();
    } catch (err) {
      notify(err instanceof ApiRequestError ? err.message : 'Cancellation failed', 'error');
    } finally {
      setCancelling(null);
    }
  }

  if (error) return <EmptyState title="Could not load bookings" description={error} />;
  if (!bookings) {
    return <div className="space-y-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-24 w-full" />)}</div>;
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">My bookings</h1>
      {bookings.length === 0 ? (
        <EmptyState
          title="No bookings yet"
          description="When you book a train, it will show up here."
          action={<Link to="/"><Button>Find a train</Button></Link>}
        />
      ) : (
        <div className="space-y-3">
          {bookings.map((booking) => (
            <Card key={booking.pnr} className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <Link to={`/bookings/${booking.pnr}`} className="font-semibold text-slate-900 hover:underline">
                    {booking.trainName}
                  </Link>
                  <StatusBadge status={booking.status} />
                </div>
                <p className="mt-1 text-sm text-slate-600">
                  PNR {booking.pnr} · {formatDate(booking.journeyDate)} · {booking.passengerCount} passenger(s)
                </p>
                <p className="mt-0.5 text-sm font-medium text-slate-800">{formatCurrency(booking.totalFare)}</p>
              </div>
              <div className="flex gap-2">
                <Link to={`/bookings/${booking.pnr}`}><Button variant="secondary">Details</Button></Link>
                {CANCELLABLE.has(booking.status) && (
                  <Button variant="danger" loading={cancelling === booking.pnr} onClick={() => onCancel(booking.pnr)}>
                    Cancel
                  </Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
