import { useEffect, useState } from 'react';
import { adminApi } from '../api/endpoints';
import { ApiRequestError } from '../api/ApiRequestError';
import type { BookingSummary, PageResponse } from '../api/types';
import { Card, EmptyState, Skeleton, StatusBadge } from '../components/ui';
import { Button } from '../components/ui';
import { formatCurrency, formatDate } from '../lib/format';

export function AdminPage() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<BookingSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setData(null);
    adminApi.allBookings(page)
      .then(setData)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'Could not load bookings'));
  }, [page]);

  if (error) return <EmptyState title="Admin unavailable" description={error} />;

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">All bookings</h1>
      {!data ? (
        <Skeleton className="h-72 w-full" />
      ) : data.content.length === 0 ? (
        <EmptyState title="No bookings" description="No bookings have been made yet." />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">PNR</th>
                <th className="px-4 py-3">Train</th>
                <th className="px-4 py-3">Date</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Fare</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((booking) => (
                <tr key={booking.pnr}>
                  <td className="px-4 py-3 font-mono text-xs text-slate-700">{booking.pnr}</td>
                  <td className="px-4 py-3">{booking.trainName} <span className="text-slate-400">#{booking.trainNumber}</span></td>
                  <td className="px-4 py-3 text-slate-600">{formatDate(booking.journeyDate)}</td>
                  <td className="px-4 py-3"><StatusBadge status={booking.status} /></td>
                  <td className="px-4 py-3 text-right font-medium">{formatCurrency(booking.totalFare)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-slate-600">
          <span>Page {data.page + 1} of {data.totalPages}</span>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={data.page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
            <Button variant="secondary" disabled={data.last} onClick={() => setPage((p) => p + 1)}>Next</Button>
          </div>
        </div>
      )}
    </div>
  );
}
