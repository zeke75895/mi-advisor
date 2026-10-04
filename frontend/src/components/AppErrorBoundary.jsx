import { ErrorBoundary } from 'react-error-boundary'
import { useLocation } from 'react-router'

function Fallback({ resetErrorBoundary }) {
  return (
    <div role="alert" className="mx-auto max-w-md rounded-xl border border-line bg-surface p-6 text-center">
      <p className="text-lg font-semibold">Something went wrong on this page</p>
      <p className="mt-1 text-sm text-ink-2">Your data is safe. Try again, or go back to your courses.</p>
      <div className="mt-4 flex justify-center gap-2">
        <button onClick={resetErrorBoundary} className="rounded-md bg-accent px-4 py-2 text-sm font-semibold text-white">
          Try again
        </button>
        <a href="/" className="rounded-md border border-line px-4 py-2 text-sm font-semibold">
          Dashboard
        </a>
      </div>
    </div>
  )
}

/** Catches render errors so one broken page never blanks the whole app; resets on navigation. */
export default function AppErrorBoundary({ children }) {
  const location = useLocation()
  return (
    <ErrorBoundary FallbackComponent={Fallback} resetKeys={[location.pathname]} onError={(e) => console.error(e)}>
      {children}
    </ErrorBoundary>
  )
}
