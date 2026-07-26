import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { bookingsApi } from '../api/endpoints';
import { ApiRequestError } from '../api/ApiRequestError';
import { TRAVEL_CLASS_LABELS } from '../api/types';
import { Button, Card } from '../components/ui';
import { useToast } from '../components/Toast';
import { useCountdown } from '../lib/useCountdown';
import { classNames, formatCurrency } from '../lib/format';
import type { PaymentNavState } from './bookingFlow';

// The payment token drives the mock provider: a normal token is approved, `tok_decline` is declined.
const PAYMENT_OPTIONS = [
  { token: 'tok_visa_demo', label: 'Card ending 4242 — will be approved' },
  { token: 'tok_decline', label: 'Card ending 0002 — will be declined' },
];

export function PaymentPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const state = useLocation().state as PaymentNavState | null;

  const [token, setToken] = useState(PAYMENT_OPTIONS[0].token);
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const countdown = useCountdown(state?.expiresAt ?? null);

  if (!state) {
    return <Navigate to="/" replace />;
  }

  async function onPay() {
    if (!state) return;
    setError(null);
    setPaying(true);
    try {
      const result = await bookingsApi.confirm(state.holdId, token);
      notify(`Booking confirmed — PNR ${result.pnr}`, 'success');
      navigate(`/bookings/${result.pnr}`, { replace: true });
    } catch (err) {
      const message = err instanceof ApiRequestError ? err.message : 'Payment failed';
      setError(message);
      notify(message, 'error');
    } finally {
      setPaying(false);
    }
  }

  const low = countdown.secondsLeft <= 60;

  return (
    <div className="mx-auto max-w-lg">
      <h1 className="text-2xl font-semibold text-slate-900">Payment</h1>

      <Card className="mt-6">
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <div>
            <p className="font-medium text-slate-800">{state.trainName ?? 'Your booking'}</p>
            <p className="text-sm text-slate-500">
              Coach {state.coachCode} · {TRAVEL_CLASS_LABELS[state.travelClass]} · {state.passengerCount} passenger(s)
            </p>
          </div>
          <div className="text-right">
            <p className="text-xs uppercase tracking-wide text-slate-400">Hold expires in</p>
            <p className={classNames('font-mono text-lg font-semibold', low ? 'text-red-600' : 'text-slate-800')}>
              {countdown.expired ? '0:00' : countdown.label}
            </p>
          </div>
        </div>

        <div className="flex items-center justify-between py-4">
          <span className="text-sm text-slate-600">Total fare</span>
          <span className="text-xl font-semibold text-slate-900">{formatCurrency(state.totalFare)}</span>
        </div>

        {countdown.expired ? (
          <div className="rounded-md bg-amber-50 p-4 text-sm text-amber-800">
            Your seat hold has expired. Please start a new booking.
            <div className="mt-3">
              <Button variant="secondary" onClick={() => navigate('/')}>Back to search</Button>
            </div>
          </div>
        ) : (
          <>
            <fieldset className="space-y-2" aria-label="Payment method">
              {PAYMENT_OPTIONS.map((option) => (
                <label key={option.token}
                  className={classNames(
                    'flex cursor-pointer items-center gap-3 rounded-md border p-3 text-sm',
                    token === option.token ? 'border-brand-500 bg-brand-50' : 'border-slate-200',
                  )}>
                  <input type="radio" name="payment" value={option.token} checked={token === option.token}
                    onChange={() => setToken(option.token)} className="accent-brand-600" />
                  {option.label}
                </label>
              ))}
            </fieldset>
            {error && <p role="alert" className="mt-3 text-sm text-red-600">{error}</p>}
            <Button onClick={onPay} loading={paying} className="mt-4 w-full">
              Pay {formatCurrency(state.totalFare)}
            </Button>
          </>
        )}
      </Card>
      <p className="mt-3 text-center text-xs text-slate-400">
        This is a demo — no real card is charged. Payment is simulated by the mock provider.
      </p>
    </div>
  );
}
