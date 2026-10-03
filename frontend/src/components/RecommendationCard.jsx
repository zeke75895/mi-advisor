import { RECOMMENDATION_TONE } from '../lib/format'
import StatusBadge from './StatusBadge'

const BORDER = {
  good: 'border-l-good',
  warning: 'border-l-warning',
  serious: 'border-l-serious',
  critical: 'border-l-critical',
}
const LABEL = {
  strong_stay: 'Stay',
  lean_stay: 'Lean stay',
  uncertain: 'Uncertain',
  lean_withdraw: 'Lean withdraw',
  strong_withdraw: 'Consider withdrawing',
}

export default function RecommendationCard({ rec, stale, onRefresh, refreshing }) {
  const tone = RECOMMENDATION_TONE[rec.recommendation] ?? 'neutral'
  const ex = rec.explanation
  const reasons = ex?.reasoning ?? rec.reasoning
  return (
    <section className={`rounded-xl border border-l-4 border-line bg-surface p-5 ${BORDER[tone] ?? ''}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <StatusBadge tone={tone}>{LABEL[rec.recommendation] ?? rec.recommendation}</StatusBadge>
        {ex?.label && <span className="rounded bg-line/60 px-2 py-0.5 text-xs font-medium text-ink-2">{ex.label}</span>}
      </div>
      <h3 className="mt-3 text-xl font-semibold">{rec.headline}</h3>
      {ex?.summary && <p className="mt-2 leading-relaxed text-ink-2">{ex.summary}</p>}

      {stale && (
        <div className="mt-3 flex flex-wrap items-center gap-2 rounded-md bg-accent-soft px-3 py-2 text-sm">
          Your ratings changed since this was calculated.
          <button onClick={onRefresh} disabled={refreshing} className="font-semibold text-accent-strong underline">
            {refreshing ? 'Updating…' : 'Update recommendation'}
          </button>
        </div>
      )}

      <h4 className="mt-4 text-sm font-semibold">Why</h4>
      <ul className="mt-1 list-disc space-y-1 pl-5 text-sm leading-relaxed">
        {reasons.map((r) => (
          <li key={r}>{r}</li>
        ))}
      </ul>

      <h4 className="mt-4 text-sm font-semibold">Next steps</h4>
      <ol className="mt-1 list-decimal space-y-1 pl-5 text-sm leading-relaxed">
        {rec.actions.map((a) => (
          <li key={a}>{a}</li>
        ))}
      </ol>

      {rec.advisorNote && (
        <p className="mt-4 rounded-md border border-line bg-page px-3 py-2 text-sm font-semibold">{rec.advisorNote}</p>
      )}
      <p className="mt-4 border-t border-line pt-3 text-xs text-ink-2">{rec.disclaimer}</p>
    </section>
  )
}
