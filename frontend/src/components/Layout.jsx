import { Link, NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import AppErrorBoundary from './AppErrorBoundary'

const navClass = ({ isActive }) =>
  `shrink-0 whitespace-nowrap rounded-md px-2.5 py-2 text-sm font-medium sm:px-3 ${isActive ? 'bg-accent-soft text-accent-strong' : 'text-ink-2 hover:bg-line/50'}`

export default function Layout() {
  const { email, logout } = useAuth()
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-2 px-4 py-3">
          <div className="flex w-full items-center justify-between sm:w-auto">
            <Link to="/" className="text-lg font-bold tracking-tight">
              Mi<span className="text-accent">Advisor</span>
            </Link>
            {/* On phones, Log out sits next to the logo so it never scrolls out of view with the links */}
            <button onClick={logout} className="rounded-md px-2.5 py-2 text-sm font-medium text-ink-2 hover:bg-line/50 sm:hidden">
              Log out
            </button>
          </div>
          {/* On narrow phones the links scroll sideways inside the bar instead of widening the page */}
          <nav className="-mx-1 flex w-full items-center gap-1 overflow-x-auto px-1 sm:mx-0 sm:w-auto sm:px-0">
            <NavLink to="/" end className={navClass}>
              Dashboard
            </NavLink>
            <NavLink to="/study-plan" className={navClass}>
              Study plan
            </NavLink>
            <NavLink to="/upload" className={navClass}>
              Upload
            </NavLink>
            <NavLink to="/insights" className={navClass}>
              Insights
            </NavLink>
            <span className="hidden max-w-40 truncate px-2 text-sm text-muted lg:inline" title={email ?? ''}>
              {email}
            </span>
            <button onClick={logout} className="hidden shrink-0 whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium text-ink-2 hover:bg-line/50 sm:inline-flex">
              Log out
            </button>
          </nav>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
        <AppErrorBoundary>
          <Outlet />
        </AppErrorBoundary>
      </main>
      <DisclaimerFooter />
    </div>
  )
}

export function DisclaimerFooter() {
  return (
    <footer className="border-t border-line bg-surface">
      <div className="mx-auto max-w-5xl px-4 py-4 text-sm text-ink-2">
        <p className="font-semibold text-ink">This is guidance, not a verdict.</p>
        <p>
          Risk predictions come from a model trained on synthetic data and can be wrong. Talk to your advisor before
          withdrawing from a course. Your data is never shared with anyone else.
        </p>
      </div>
    </footer>
  )
}
