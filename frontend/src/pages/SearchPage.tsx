import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { trainsApi } from '../api/endpoints';
import { ApiRequestError } from '../api/ApiRequestError';
import type { TrainSearchResult } from '../api/types';
import { TRAVEL_CLASS_LABELS } from '../api/types';
import { Button, Card, EmptyState, Input, Skeleton } from '../components/ui';
import { formatDate, formatDuration, formatTime } from '../lib/format';

const COMMON_STATIONS = ['NDLS', 'BCT', 'CSTM', 'HWH', 'MAS', 'SBC', 'ADI', 'PUNE', 'JP', 'LKO'];

function todayPlus(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

export function SearchPage() {
  const navigate = useNavigate();
  const [from, setFrom] = useState('NDLS');
  const [to, setTo] = useState('BCT');
  const [date, setDate] = useState(todayPlus(1));
  const [results, setResults] = useState<TrainSearchResult[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSearch(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    setResults(null);
    try {
      const page = await trainsApi.search(from.trim().toUpperCase(), to.trim().toUpperCase(), date);
      setResults(page.content);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Search failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">Find trains</h1>

      <Card className="mb-8">
        <form onSubmit={onSearch} className="grid gap-4 sm:grid-cols-4" aria-label="Search trains">
          <Input label="From" list="stations" required value={from} onChange={(e) => setFrom(e.target.value)} />
          <Input label="To" list="stations" required value={to} onChange={(e) => setTo(e.target.value)} />
          <Input label="Date" type="date" required min={todayPlus(0)} value={date} onChange={(e) => setDate(e.target.value)} />
          <div className="flex items-end">
            <Button type="submit" loading={loading} className="w-full">Search</Button>
          </div>
          <datalist id="stations">
            {COMMON_STATIONS.map((code) => <option key={code} value={code} />)}
          </datalist>
        </form>
      </Card>

      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}

      {loading && (
        <div className="space-y-3">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-24 w-full" />)}
        </div>
      )}

      {results !== null && !loading && results.length === 0 && (
        <EmptyState title="No trains found" description="Try a different date or station pair — remember trains run in one direction only." />
      )}

      <div className="space-y-3">
        {results?.map((train) => (
          <Card key={train.scheduleId} className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="flex items-center gap-2">
                <span className="font-semibold text-slate-900">{train.trainName}</span>
                <span className="text-sm text-slate-500">#{train.trainNumber}</span>
              </div>
              <p className="mt-1 text-sm text-slate-600">
                {formatTime(train.departureTime)} → {formatTime(train.arrivalTime)} · {formatDuration(train.durationMinutes)} · {train.distanceKm} km
              </p>
              <p className="mt-0.5 text-xs text-slate-400">{formatDate(train.journeyDate)}</p>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {train.availability.map((cls) => (
                  <span key={cls.travelClass}
                    className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs text-slate-700">
                    {TRAVEL_CLASS_LABELS[cls.travelClass]}
                    <span className={cls.availableSeats > 0 ? 'font-semibold text-emerald-600' : 'text-red-500'}>
                      {cls.availableSeats > 0 ? `${cls.availableSeats} left` : 'full'}
                    </span>
                  </span>
                ))}
              </div>
            </div>
            <Button
              variant="secondary"
              onClick={() => navigate(`/book/${train.scheduleId}/seats`, {
                state: { trainName: train.trainName, trainNumber: train.trainNumber, journeyDate: train.journeyDate },
              })}
            >
              Select seats
            </Button>
          </Card>
        ))}
      </div>
    </div>
  );
}
