import { type ChangeEvent, type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Button, Card, Input } from '../components/ui';
import { ApiRequestError } from '../api/ApiRequestError';

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ fullName: '', email: '', password: '', phone: '' });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (key: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setSubmitting(true);
    try {
      await register({ ...form, phone: form.phone || undefined });
      navigate('/bookings', { replace: true });
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.message);
        setFieldErrors(Object.fromEntries(err.fieldErrors.map((f) => [f.field, f.message])));
      } else {
        setError('Could not create your account');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">Create your account</h1>
      <Card>
        <form onSubmit={onSubmit} className="space-y-4" aria-label="Register">
          <Input label="Full name" required value={form.fullName} onChange={update('fullName')}
            error={fieldErrors.fullName} />
          <Input label="Email" type="email" autoComplete="email" required value={form.email}
            onChange={update('email')} error={fieldErrors.email} />
          <Input label="Password" type="password" autoComplete="new-password" required minLength={8}
            value={form.password} onChange={update('password')} error={fieldErrors.password}
            placeholder="At least 8 characters" />
          <Input label="Phone (optional)" value={form.phone} onChange={update('phone')} error={fieldErrors.phone} />
          {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
          <Button type="submit" loading={submitting} className="w-full">Create account</Button>
        </form>
      </Card>
      <p className="mt-4 text-center text-sm text-slate-600">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-brand-700 hover:underline">Sign in</Link>
      </p>
    </div>
  );
}
