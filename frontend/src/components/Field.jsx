export const inputClass =
  'mt-1 w-full rounded-md border border-line bg-white px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30'

export default function Field({ label, hint, error, children }) {
  return (
    <label className="block text-sm">
      <span className="font-medium">{label}</span>
      {children}
      {hint && !error && <span className="mt-1 block text-xs text-muted">{hint}</span>}
      {error && <span className="mt-1 block text-xs font-medium text-critical">{error}</span>}
    </label>
  )
}

export function ErrorBanner({ error, onRetry }) {
  if (!error) return null
  return (
    <div role="alert" className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md border border-critical/40 bg-critical/5 px-3 py-2 text-sm text-ink">
      <span className="flex-1">{error}</span>
      {onRetry && (
        <button type="button" onClick={onRetry} className="font-semibold text-accent-strong underline">
          Try again
        </button>
      )}
    </div>
  )
}

export function Loading({ label = 'Loading…', className = '' }) {
  return (
    <p role="status" aria-live="polite" className={`flex items-center gap-2 text-sm text-ink-2 ${className}`}>
      <span aria-hidden className="h-4 w-4 animate-spin rounded-full border-2 border-accent/30 border-t-accent" />
      {label}
    </p>
  )
}

export function Button({ variant = 'primary', className = '', ...props }) {
  const styles =
    variant === 'primary'
      ? 'bg-accent text-white hover:bg-accent-strong disabled:opacity-60'
      : 'border border-line bg-surface text-ink hover:bg-line/40 disabled:opacity-60'
  return <button className={`rounded-md px-4 py-2 text-sm font-semibold ${styles} ${className}`} {...props} />
}
