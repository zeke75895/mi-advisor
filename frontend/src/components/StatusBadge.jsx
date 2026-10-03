// Status color is never the only cue: every badge pairs a colored icon with a text label.
const TONES = {
  good: { color: 'bg-good', icon: '✓' },
  warning: { color: 'bg-warning', icon: '!' },
  serious: { color: 'bg-serious', icon: '▲' },
  critical: { color: 'bg-critical', icon: '✕' },
  neutral: { color: 'bg-line', icon: '–' },
}

export default function StatusBadge({ tone = 'neutral', children }) {
  const t = TONES[tone] ?? TONES.neutral
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-line bg-surface px-2.5 py-1 text-xs font-semibold text-ink">
      <span aria-hidden className={`grid h-4 w-4 place-items-center rounded-full text-[10px] text-white ${t.color}`}>
        {t.icon}
      </span>
      {children}
    </span>
  )
}

const RISK = {
  high: { tone: 'critical', label: 'High risk' },
  moderate: { tone: 'warning', label: 'Moderate risk' },
  low: { tone: 'good', label: 'Low risk' },
}

export function RiskBadge({ level }) {
  const r = RISK[level]
  return r ? <StatusBadge tone={r.tone}>{r.label}</StatusBadge> : <StatusBadge>Not assessed</StatusBadge>
}
