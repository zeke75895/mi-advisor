import { letterGrade } from '../lib/format'

const PASS_LINE = 70

/** Meter for the projected final grade (0-100) with the C threshold marked. */
export default function GradeMeter({ projection, compact = false }) {
  const projected = projection?.projectedFinal
  if (projected == null) {
    return (
      <p className="text-sm text-ink-2">
        {projection?.itemCount ? 'Rate or grade an item to see a projection.' : 'Add graded items to see a projection.'}
      </p>
    )
  }
  const width = Math.max(0, Math.min(100, projected))
  return (
    <div>
      <div className="flex items-baseline justify-between">
        <span className="text-3xl font-bold tabular-nums">
          {Math.round(projected)}
          <span className="text-base font-medium text-ink-2"> / 100 · {letterGrade(projected)}</span>
        </span>
        {!compact && <span className="text-sm text-ink-2">{Math.round(projection.remainingWeight * 100)}% of grade still ahead</span>}
      </div>
      <div
        className="relative mt-3 h-3 rounded-full bg-accent-soft"
        role="meter"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(projected)}
        aria-label="Projected final grade"
      >
        <div className="h-3 rounded-full bg-accent" style={{ width: `${width}%` }} />
        <div className="absolute -top-1 h-5 w-0.5 bg-ink" style={{ left: `${PASS_LINE}%` }} title="C threshold (70)" />
      </div>
      <div className="relative mt-1 h-4 text-xs text-muted">
        <span className="absolute left-0">0</span>
        <span className="absolute -translate-x-1/2" style={{ left: `${PASS_LINE}%` }}>
          C (70)
        </span>
        <span className="absolute right-0">100</span>
      </div>
      {!compact && <NeededForC projection={projection} />}
      {!compact && (
        <p className="mt-2 text-sm text-ink-2">
          {projection.currentGrade != null
            ? `Current grade on graded work: ${Math.round(projection.currentGrade)}.`
            : 'Nothing graded yet, so this projection is based on your confidence ratings.'}
          {projection.coveredWeight < 1 && ` Based on ${Math.round(projection.coveredWeight * 100)}% of the course grade.`}
        </p>
      )}
    </div>
  )
}

/** The average needed on everything still ahead to finish with a C, from actual grades only. */
function NeededForC({ projection }) {
  const r = projection.requiredScore
  const left = Math.round(projection.remainingWeight * 100)
  let text
  if (r == null) text = 'All graded work is in.'
  else if (r <= 0) text = 'You’ve already earned enough points for a C.'
  else if (r > 100) text = `A C is no longer reachable: the best possible final grade is ${Math.floor(projection.maxPossible)}.`
  else text = `To finish with a C, you need an average of ${Math.ceil(r)}% on the remaining ${left}%.`
  const steep = r != null && r > 85
  return (
    <p className={`mt-3 rounded-md px-3 py-2 text-sm font-medium ${steep ? 'bg-critical/5 text-ink' : 'bg-accent-soft text-ink'}`}>
      {text}
    </p>
  )
}
