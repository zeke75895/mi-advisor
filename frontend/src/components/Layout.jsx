import { Link, NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthContext'

const navClass = ({ isActive }) =>
  `rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-accent-soft text-accent-strong' : 'text-ink-2 hover:bg-line/50'}`

export default function Layout() {
  const { email, logout } = useAuth()
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-2 px-4 py-3">
          <Link to="/" className="text-lg font-bold tracking-tight">
            Course<span className="text-accent">Compass</span>
          </Link>
          <nav className="flex items-center gap-1">
            <NavLink to="/" end className={navClass}>
              Dashboard
            </NavLink>
            <NavLink to="/upload" className={navClass}>
              Upload syllabus
            </NavLink>
            <span className="hidden px-2 text-sm text-muted sm:inline" title={email ?? ''}>
              {email}
            </span>
            <button onClick={logout} className="rounded-md px-3 py-2 text-sm font-medium text-ink-2 hover:bg-line/50">
              Log out
            </button>
          </nav>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
        <Outlet />
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
