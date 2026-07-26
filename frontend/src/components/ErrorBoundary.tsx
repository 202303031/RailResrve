import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
}
interface State {
  error: Error | null;
}

/** Catches render-time errors anywhere below it so one broken screen doesn't blank the whole app. */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Unhandled UI error', error, info);
  }

  render(): ReactNode {
    if (this.state.error) {
      return (
        <div className="mx-auto mt-24 max-w-md rounded-lg border border-red-200 bg-white p-8 text-center shadow-sm">
          <h1 className="text-lg font-semibold text-slate-900">Something went wrong</h1>
          <p className="mt-2 text-sm text-slate-600">
            An unexpected error occurred. Try reloading the page.
          </p>
          <button
            onClick={() => window.location.reload()}
            className="mt-6 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            Reload
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
