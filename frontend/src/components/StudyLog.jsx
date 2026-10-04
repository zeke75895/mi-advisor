import { useState } from 'react'
import { sessionApi } from '../api/client'
import { hoursMinutes, localDate } from '../lib/format'
import Field, { Button, ErrorBanner, Loading, inputClass } from './Field'

const QUICK = [25, 50, 90]

/** Log study time for this course and see how much you've actually studied (user story US10). */
export default function StudyLog({ courseId, log, loading, error, onReload }) {
  const [minutes, setMinutes] = useState('')
  const [date, setDate] = useState(localDate())
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState(null)

  async function save(mins) {
    setBusy(true)
    setFormError(null)
    try {
      await sessionApi.log(courseId, { minutes: mins, studiedOn: date, note: note || null }, localDate())
      setMinutes('')
      setNote('')
      onReload()
    } catch (e) {
      const fields = Object.values(e.fieldErrors ?? {})
      setFormError(fields.length ? fields.join(' · ') : e.message)
    } finally {
      setBusy(false)
    }
  }

  async function remove(id) {
    setFormError(null)
    try {
      await sessionApi.remove(id)
      onReload()
    } catch (e) {
      setFormError(`Couldn't delete that session: ${e.message}`)
    }
  }

  const s = log?.summary
  return (
    <section className="rounded-xl border border-line bg-surface p-5">
      <h2 className="font-semibold">Study log</h2>
      <p className="mt-1 text-sm text-ink-2">Track how much you're actually studying for this course.</p>

      {loading && !log && <Loading label="Loading your study log…" className="mt-3" />}
      <ErrorBanner error={error && `Couldn't load your study log: ${error}`} onRetry={onReload} />

      {s && (
        <dl className="mt-4 grid grid-cols-3 gap-2 text-center">
          <Stat label="Last 7 days" value={hoursMinutes(s.last7DaysMinutes)} />
          <Stat label="Avg per week" value={`${s.avgWeeklyHours} h`} hint="up to 4 weeks" />
          <Stat label="Sessions" value={s.totalSessions} hint="all time" />
        </dl>
      )}

      <form
        className="mt-4 space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          save(Number(minutes))
        }}
      >
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">Quick log:</span>
          {QUICK.map((m) => (
            <Button key={m} type="button" variant="secondary" disabled={busy} onClick={() => save(m)} className="px-3 py-1.5">
              +{m} min
            </Button>
          ))}
        </div>
        <div className="grid gap-3 sm:grid-cols-[1fr_1fr_2fr_auto] sm:items-end">
          <Field label="Minutes">
            <input type="number" min="5" max="720" className={inputClass} value={minutes} onChange={(e) => setMinutes(e.target.value)} />
          </Field>
          <Field label="Date">
            <input type="date" max={localDate()} className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} />
          </Field>
          <Field label="Note (optional)">
            <input maxLength={200} className={inputClass} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Chapter 3 problems" />
          </Field>
          <Button type="submit" disabled={busy || !minutes}>
            {busy ? 'Saving…' : 'Log'}
          </Button>
        </div>
        <ErrorBanner error={formError} />
      </form>

      {log?.sessions?.length > 0 && (
        <ul className="mt-4 divide-y divide-line border-t border-line text-sm">
          {log.sessions.slice(0, 8).map((session) => (
            <li key={session.id} className="flex items-center gap-3 py-2">
              <span className="w-20 shrink-0 text-ink-2">
                {new Date(`${session.studiedOn}T12:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
              </span>
              <span className="w-16 shrink-0 font-semibold tabular-nums">{hoursMinutes(session.minutes)}</span>
              <span className="min-w-0 flex-1 truncate text-ink-2">{session.note}</span>
              <button
                type="button"
                onClick={() => remove(session.id)}
                className="shrink-0 rounded px-2 py-1 text-xs font-semibold text-ink-2 hover:bg-critical/10"
                aria-label={`Delete ${session.minutes}-minute session on ${session.studiedOn}`}
              >
                Delete
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function Stat({ label, value, hint }) {
  return (
    <div className="rounded-lg bg-page p-2">
      <dt className="text-xs text-ink-2">{label}</dt>
      <dd className="text-lg font-bold tabular-nums">{value}</dd>
      {hint && <dd className="text-[11px] text-muted">{hint}</dd>}
    </div>
  )
}
