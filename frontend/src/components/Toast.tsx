import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { classNames } from '../lib/format';

type ToastKind = 'success' | 'error' | 'info';
interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastContextValue {
  notify: (message: string, kind?: ToastKind) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);
let nextId = 1;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const notify = useCallback((message: string, kind: ToastKind = 'info') => {
    const id = nextId++;
    setToasts((current) => [...current, { id, kind, message }]);
    setTimeout(() => dismiss(id), 4000);
  }, [dismiss]);

  const value = useMemo(() => ({ notify }), [notify]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="fixed bottom-4 right-4 z-50 flex w-full max-w-sm flex-col gap-2" role="status" aria-live="polite">
        {toasts.map((toast) => (
          <button
            key={toast.id}
            onClick={() => dismiss(toast.id)}
            className={classNames(
              'rounded-md px-4 py-3 text-left text-sm shadow-lg ring-1 transition',
              toast.kind === 'success' && 'bg-emerald-50 text-emerald-800 ring-emerald-200',
              toast.kind === 'error' && 'bg-red-50 text-red-800 ring-red-200',
              toast.kind === 'info' && 'bg-slate-800 text-slate-50 ring-slate-700',
            )}
          >
            {toast.message}
          </button>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
}
