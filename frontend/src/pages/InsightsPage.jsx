import { ErrorBanner, Loading } from '../components/Field'
import RaiPanel from '../components/RaiPanel'
import { FEATURE_LABELS } from '../lib/format'
import { useModelInfo } from '../lib/ModelInfoContext'

const PERCENT = new Set(['attendance_rate', 'on_time_submission_rate', 'late_night_study_pct'])
const pct = (x, digits = 0) => `${(x * 100).toFixed(digits)}%`

function formatThreshold(feature, value) {
  return PERCENT.has(feature) ? `${Math.round(value * 1000) / 10}%` : `${value}`
}

// Phrasings that read naturally before "is at or below ..."
const RULE_LABELS = {
  attendance_rate: 'class attendance',
  missed_deadlines: 'the number of missed deadlines',
  on_time_submission_rate: 'the on-time submission rate',
  avg_practice_quiz_score: 'the practice quiz average',
  midterm_score: 'the midterm score',
  avg_weekly_study_hours: 'weekly study time (hours)',
  flashcards_reviewed: 'the number of flashcards reviewed',
  avg_days_started_before_exam: 'the head start before exams (days)',
  late_night_study_pct: 'the share of studying done 12–4 a.m.',
  avg_sleep_hours: 'average sleep (hours)',
  study_sessions_logged: 'the number of study sessions',
}

/** "the midterm score is at or below 64.5" */
function conditionText({ feature, op, threshold }) {
  const label = RULE_LABELS[feature] ?? (FEATURE_LABELS[feature] ?? feature).toLowerCase()
  return `${label} is ${op === '<=' ? 'at or below' : 'above'} ${formatThreshold(feature, threshold)}`
}

export default function InsightsPage() {
  const { info, loading, error, reload } = useModelInfo()

  if (loading && !info) return <Loading label="Loading model insights…" />
  if (error && !info) return <ErrorBanner error={`Couldn't load model insights: ${error}`} onRetry={reload} />
  if (!info) return null

  const m = info.metrics
  const cm = m.confusion_matrix
  const atRisk = cm.tp + cm.fn
  const importances = Object.entries(info.feature_importances).sort((a, b) => b[1] - a[1])
  const maxImportance = importances[0]?.[1] ?? 1
  const leaves = [...(info.leaves ?? [])].sort((a, b) => b.flagged - a.flagged || b.train_students - a.train_students)

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold">Model insights</h1>
        <p className="text-sm text-ink-2">
          How the risk model works and how well it does on {info.n_test} students it never saw during training.
          Decision tree ({info.model_version}, depth {info.hyperparameters.max_depth}) trained on {info.n_train}{' '}
          synthetic student records.
        </p>
      </div>

      <section aria-labelledby="headline" className="space-y-3">
        <h2 id="headline" className="sr-only">
          Headline numbers
        </h2>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <Stat label="At-risk students caught (recall)" value={pct(m.recall_at_risk)} note={`${cm.tp} of ${atRisk} in the test set`} />
          <Stat label="Accuracy" value={pct(m.test_accuracy)} note={`vs ${pct(info.baseline_metrics.accuracy, 1)} for guessing "fine"`} />
          <Stat label="Precision when flagged" value={pct(m.precision_at_risk)} note={`${cm.fp} false alarms`} />
          <Stat label="ROC-AUC" value={m.roc_auc.toFixed(2)} note="0.5 is a coin flip" />
        </div>
      </section>

      <section className="rounded-xl border border-line bg-surface p-5">
        <h2 className="font-semibold">Why recall, not accuracy</h2>
        <p className="mt-2 text-sm leading-relaxed text-ink-2">
          Only about 28% of students are at risk, so a "model" that says everyone is fine already scores{' '}
          {pct(info.baseline_metrics.accuracy, 1)} accuracy while catching <strong className="text-ink">none</strong> of
          them. Missing a struggling student costs far more than a false alarm, so the tree is tuned to catch as many
          at-risk students as possible.
        </p>
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[22rem] text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-muted">
              <tr>
                <th className="py-2 pr-4 font-semibold"> </th>
                <th className="py-2 pr-4 font-semibold">Always "fine"</th>
                <th className="py-2 font-semibold">Decision tree</th>
              </tr>
            </thead>
            <tbody className="tabular-nums">
              <tr className="border-t border-line">
                <td className="py-2 pr-4">Accuracy</td>
                <td className="py-2 pr-4">{pct(info.baseline_metrics.accuracy, 1)}</td>
                <td className="py-2 font-semibold">{pct(m.test_accuracy, 1)}</td>
              </tr>
              <tr className="border-t border-line">
                <td className="py-2 pr-4">Recall on at-risk</td>
                <td className="py-2 pr-4">{pct(info.baseline_metrics.recall_at_risk, 1)}</td>
                <td className="py-2 font-semibold">{pct(m.recall_at_risk, 1)}</td>
              </tr>
              <tr className="border-t border-line">
                <td className="py-2 pr-4">Train vs test accuracy</td>
                <td className="py-2 pr-4">—</td>
                <td className="py-2">
                  {pct(m.train_accuracy, 1)} vs {pct(m.test_accuracy, 1)} (gap {pct(m.train_accuracy - m.test_accuracy, 1)}, little overfitting)
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <ConfusionMatrix cm={cm} n={info.n_test} />
        <Importances importances={importances} max={maxImportance} unused={info.unused_features ?? []} />
      </div>

      <section className="rounded-xl border border-line bg-surface p-5">
        <h2 className="font-semibold">What the tree learned, in plain English</h2>
        <p className="mt-1 text-sm text-ink-2">
          Every student follows exactly one of these paths. Counts are from the {info.n_train} training students.
        </p>
        <ul className="mt-4 space-y-3">
          {leaves.map((leaf, i) => {
            const share = leaf.train_students ? leaf.train_at_risk / leaf.train_students : 0
            return (
              <li key={i} className={`rounded-lg border p-3 text-sm ${leaf.flagged ? 'border-critical/40 bg-critical/5' : 'border-line bg-page'}`}>
                <p className="leading-relaxed">
                  <span className="font-semibold">If </span>
                  {leaf.conditions.map(conditionText).join(', and ')}
                  <span className="font-semibold"> → {leaf.flagged ? 'flagged as at risk' : 'not flagged'}</span>
                </p>
                <p className="mt-1 text-xs text-ink-2">
                  {leaf.train_at_risk} of {leaf.train_students} training students on this path ({pct(share)}) actually
                  finished with a D or F.
                </p>
              </li>
            )
          })}
        </ul>
        <p className="mt-4 text-sm leading-relaxed text-ink-2">
          <strong className="text-ink">In one sentence:</strong> a weak midterm, late assignments and missed classes are
          the warning signs, and lots of late-night studying tips a borderline student over the edge. The model never
          uses sleep, study hours, flashcards or quiz scores, so tips about those in the app are general study advice,
          not reasons a course was flagged.
        </p>
      </section>

      <RaiPanel />
      <p className="text-xs text-muted">
        Full training and evaluation: <code>notebooks/02_train_evaluate.ipynb</code> in the project repository.
      </p>
    </div>
  )
}

function Stat({ label, value, note }) {
  return (
    <div className="rounded-xl border border-line bg-surface p-4">
      <p className="text-xs font-medium text-ink-2">{label}</p>
      <p className="mt-1 text-3xl font-bold tabular-nums">{value}</p>
      <p className="mt-1 text-xs text-muted">{note}</p>
    </div>
  )
}

function ConfusionMatrix({ cm, n }) {
  const max = Math.max(cm.tn, cm.fp, cm.fn, cm.tp)
  // One-hue sequential scale: darker = more students
  const cell = (count, label, meaning) => {
    const strength = count / max
    const dark = strength > 0.55
    return (
      <div
        className="flex min-h-24 flex-col justify-between rounded-lg p-3"
        style={{ backgroundColor: `color-mix(in oklab, #184f95 ${Math.round(8 + strength * 80)}%, #eef4fc)` }}
      >
        <span className={`text-2xl font-bold tabular-nums ${dark ? 'text-white' : 'text-ink'}`}>{count}</span>
        <span className={`text-xs leading-snug ${dark ? 'text-white' : 'text-ink-2'}`}>
          <span className="font-semibold">{label}</span>
          <br />
          {meaning}
        </span>
      </div>
    )
  }
  return (
    <section className="rounded-xl border border-line bg-surface p-5">
      <h2 className="font-semibold">Confusion matrix</h2>
      <p className="mt-1 text-sm text-ink-2">{n} test students the model never saw.</p>
      <div className="mt-4 grid grid-cols-[auto_1fr_1fr] gap-2 text-xs">
        <span />
        <span className="text-center font-semibold text-ink-2">Predicted fine</span>
        <span className="text-center font-semibold text-ink-2">Predicted at risk</span>
        <span className="flex items-center font-semibold text-ink-2 [writing-mode:vertical-rl] rotate-180">Actually fine</span>
        {cell(cm.tn, 'True negative', 'correctly left alone')}
        {cell(cm.fp, 'False alarm', 'got an extra nudge')}
        <span className="flex items-center font-semibold text-ink-2 [writing-mode:vertical-rl] rotate-180">At risk</span>
        {cell(cm.fn, 'Missed', 'no warning given')}
        {cell(cm.tp, 'Caught', 'flagged in time')}
      </div>
    </section>
  )
}

function Importances({ importances, max, unused }) {
  return (
    <section className="rounded-xl border border-line bg-surface p-5">
      <h2 className="font-semibold">What drives the prediction</h2>
      <p className="mt-1 text-sm text-ink-2">Feature importance: share of the tree's decision-making each input accounts for.</p>
      <ul className="mt-4 space-y-3">
        {importances.map(([name, value]) => (
          <li key={name}>
            <div className="flex justify-between text-sm">
              <span>{FEATURE_LABELS[name] ?? name}</span>
              <span className="font-semibold tabular-nums">{pct(value)}</span>
            </div>
            <div className="mt-1 h-2.5 rounded-full bg-accent-soft">
              <div className="h-2.5 rounded-full bg-accent" style={{ width: `${(value / max) * 100}%` }} />
            </div>
          </li>
        ))}
      </ul>
      {unused.length > 0 && (
        <div className="mt-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted">Not used by the tree</p>
          <ul className="mt-2 flex flex-wrap gap-1.5">
            {unused.map((f) => (
              <li key={f} className="rounded bg-line/60 px-2 py-0.5 text-xs text-ink-2">
                {FEATURE_LABELS[f] ?? f}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}
