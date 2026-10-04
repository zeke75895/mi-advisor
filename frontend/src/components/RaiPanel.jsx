import { Link } from 'react-router'
import { recallPct, useModelInfo } from '../lib/ModelInfoContext'

/** Responsible AI panel shown on every risk and recommendation card. */
export default function RaiPanel({ compact = false }) {
  const { info } = useModelInfo()
  const recall = recallPct(info)
  const recallText =
    recall != null
      ? `Model trained on synthetic data — recall on at-risk students is ${recall}%`
      : 'Model trained on synthetic data — see Model insights for its recall'

  if (compact) {
    return (
      <p className="text-xs leading-relaxed text-ink-2">
        Guidance, not a verdict · Synthetic-data model{recall != null && `, ${recall}% recall`} · Talk to your advisor
        before withdrawing
      </p>
    )
  }

  const items = [
    { icon: 'ⓘ', text: 'This is guidance, not a verdict' },
    { icon: '◎', text: recallText, link: true },
    { icon: '☎', text: 'Talk to your advisor before withdrawing' },
    { icon: '✦', text: 'AI-generated content is labeled' },
  ]
  return (
    <aside aria-label="Responsible AI" className="rounded-lg border border-line bg-page p-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted">Responsible AI</p>
      <ul className="mt-2 space-y-1.5 text-xs leading-snug text-ink-2">
        {items.map((item) => (
          <li key={item.text} className="flex gap-2">
            <span aria-hidden className="w-4 shrink-0 text-center text-accent-strong">
              {item.icon}
            </span>
            <span>
              {item.text}
              {item.link && (
                <>
                  {' · '}
                  <Link to="/insights" className="font-semibold text-accent-strong underline">
                    How the model works
                  </Link>
                </>
              )}
            </span>
          </li>
        ))}
      </ul>
    </aside>
  )
}
