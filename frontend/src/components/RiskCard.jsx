import { FEATURE_LABELS, riskLevel } from '../lib/format'
import { RiskBadge } from './StatusBadge'

/** Result of POST /predict: level only (the probability isn't calibrated), plus the plain-language "why". */
export default function RiskCard({ result }) {
  const level = riskLevel(result.riskProbability)
  return (
    <section className="rounded-xl border border-line bg-surface p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-semibold">Risk check</h3>
        <RiskBadge level={level} />
      </div>
      <p className="mt-3 text-sm leading-relaxed">{result.explanation}</p>
      {result.topFeatures?.length > 0 && (
        <div className="mt-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted">What mattered for you</p>
          <ul className="mt-1 flex flex-wrap gap-2">
            {result.topFeatures.map((f) => (
              <li key={f.name} className="rounded-md bg-accent-soft px-2 py-1 text-xs text-ink">
                {FEATURE_LABELS[f.name] ?? f.name}
              </li>
            ))}
          </ul>
        </div>
      )}
      {result.imputedFeatures?.length > 0 && (
        <p className="mt-3 text-xs text-ink-2">
          You left {result.imputedFeatures.map((f) => (FEATURE_LABELS[f] ?? f).toLowerCase()).join(' and ')} blank, so a
          typical value was used.
        </p>
      )}
      <p className="mt-3 border-t border-line pt-3 text-xs text-ink-2">{result.disclaimer}</p>
    </section>
  )
}
