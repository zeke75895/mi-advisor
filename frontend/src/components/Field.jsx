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

export function ErrorBanner({ error }) {
  if (!error) return null
  return (
    <p role="alert" className="rounded-md border border-critical/40 bg-critical/5 px-3 py-2 text-sm text-ink">
      {error}
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
