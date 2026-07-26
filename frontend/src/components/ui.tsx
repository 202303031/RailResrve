import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react';
import { classNames } from '../lib/format';

export function Button({
  children,
  variant = 'primary',
  loading = false,
  className,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'danger'; loading?: boolean }) {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      className={classNames(
        'inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-60',
        variant === 'primary' && 'bg-brand-600 text-white hover:bg-brand-700',
        variant === 'secondary' && 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50',
        variant === 'danger' && 'bg-red-600 text-white hover:bg-red-700',
        className,
      )}
    >
      {loading && <Spinner className="h-4 w-4" />}
      {children}
    </button>
  );
}

export function Input({
  label,
  error,
  id,
  className,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label?: string; error?: string }) {
  return (
    <label className="block">
      {label && <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>}
      <input
        id={id}
        {...props}
        className={classNames(
          'w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100',
          error && 'border-red-400 focus:border-red-400 focus:ring-red-100',
          className,
        )}
      />
      {error && <span className="mt-1 block text-xs text-red-600">{error}</span>}
    </label>
  );
}

export function Select({
  label,
  id,
  className,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label?: string }) {
  return (
    <label className="block">
      {label && <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>}
      <select
        id={id}
        {...props}
        className={classNames(
          'w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100',
          className,
        )}
      >
        {children}
      </select>
    </label>
  );
}

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={classNames('rounded-lg border border-slate-200 bg-white p-5 shadow-sm', className)}>
      {children}
    </div>
  );
}

export function Spinner({ className }: { className?: string }) {
  return (
    <svg className={classNames('animate-spin text-current', className ?? 'h-5 w-5')} viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
    </svg>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={classNames('animate-pulse rounded bg-slate-200', className)} aria-hidden />;
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-300 bg-white px-6 py-12 text-center">
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      {description && <p className="mx-auto mt-1 max-w-sm text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

const STATUS_STYLES: Record<string, string> = {
  CONFIRMED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  HELD: 'bg-amber-50 text-amber-700 ring-amber-200',
  WAITLISTED: 'bg-sky-50 text-sky-700 ring-sky-200',
  RAC: 'bg-indigo-50 text-indigo-700 ring-indigo-200',
  CANCELLED: 'bg-slate-100 text-slate-600 ring-slate-200',
  EXPIRED: 'bg-slate-100 text-slate-500 ring-slate-200',
  PENDING: 'bg-slate-100 text-slate-600 ring-slate-200',
};

export function StatusBadge({ status }: { status: string }) {
  return (
    <span className={classNames(
      'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1',
      STATUS_STYLES[status] ?? 'bg-slate-100 text-slate-600 ring-slate-200',
    )}>
      {status}
    </span>
  );
}
