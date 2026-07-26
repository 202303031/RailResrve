import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { classNames } from '../lib/format';

function navClass({ isActive }: { isActive: boolean }): string {
  return classNames(
    'rounded-md px-3 py-1.5 text-sm font-medium transition',
    isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:text-slate-900',
  );
}

export function Layout() {
  const { isAuthenticated, isAdmin, user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="flex items-center gap-2 text-lg font-semibold text-slate-900">
            <span className="grid h-7 w-7 place-items-center rounded bg-brand-600 text-sm font-bold text-white">R</span>
            RailReserve
          </Link>
          <nav className="flex items-center gap-1">
            <NavLink to="/" className={navClass} end>Search</NavLink>
            {isAuthenticated && <NavLink to="/bookings" className={navClass}>My Bookings</NavLink>}
            {isAdmin && <NavLink to="/admin" className={navClass}>Admin</NavLink>}
          </nav>
          <div className="flex items-center gap-2">
            {isAuthenticated ? (
              <>
                <span className="hidden text-sm text-slate-500 sm:inline">{user?.email ?? 'Account'}</span>
                <button
                  onClick={() => { logout(); navigate('/'); }}
                  className="rounded-md px-3 py-1.5 text-sm font-medium text-slate-600 ring-1 ring-slate-300 hover:bg-slate-50"
                >
                  Sign out
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="rounded-md px-3 py-1.5 text-sm font-medium text-slate-600 hover:text-slate-900">
                  Sign in
                </Link>
                <Link to="/register" className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700">
                  Register
                </Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="border-t border-slate-200 py-4 text-center text-xs text-slate-400">
        RailReserve — portfolio project
      </footer>
    </div>
  );
}
