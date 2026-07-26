import { FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Button, Card, Input } from '../components/ui';
import { ApiRequestError } from '../api/ApiRequestError';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/bookings';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate(redirectTo, { replace: true });
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Could not sign in');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">Sign in</h1>
      <Card>
        <form onSubmit={onSubmit} className="space-y-4" aria-label="Sign in">
          <Input label="Email" type="email" autoComplete="email" required value={email}
            onChange={(e) => setEmail(e.target.value)} />
          <Input label="Password" type="password" autoComplete="current-password" required value={password}
            onChange={(e) => setPassword(e.target.value)} />
          {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
          <Button type="submit" loading={submitting} className="w-full">Sign in</Button>
        </form>
      </Card>
      <p className="mt-4 text-center text-sm text-slate-600">
        No account?{' '}
        <Link to="/register" className="font-medium text-brand-700 hover:underline">Create one</Link>
      </p>
    </div>
  );
}
