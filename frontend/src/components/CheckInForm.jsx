import { useEffect, useState } from 'react'
import { courseApi } from '../api/client'
import Field, { Button, ErrorBanner, inputClass } from './Field'

// Percent fields are typed as 0-100 here and sent to the API as 0-1 fractions.
const FIELDS = [
  { key: 'attendanceRate', label: 'Classes attended', unit: '%', percent: true, required: true },
  { key: 'onTimeSubmissionRate', label: 'Assignments submitted on time', unit: '%', percent: true, required: true },
  { key: 'missedDeadlines', label: 'Deadlines missed so far', unit: '', required: true, integer: true },
  { key: 'midtermScore', label: 'Midterm score', unit: '/100', hint: 'Leave blank to use your graded "Midterm" item' },
  { key: 'avgPracticeQuizScore', label: 'Practice quiz average', unit: '/100', hint: 'Leave blank if you took none' },
  { key: 'avgWeeklyStudyHours', label: 'Study hours per week', unit: 'h', required: true },
  { key: 'studySessionsLogged', label: 'Study sessions so far', unit: '', required: true, integer: true },
  { key: 'flashcardsReviewed', label: 'Flashcards reviewed', unit: '', required: true, integer: true },
  { key: 'avgDaysStartedBeforeExam', label: 'Days you start studying before an exam', unit: 'days', required: true },
  { key: 'lateNightStudyPct', label: 'Studying done 12–4 a.m.', unit: '%', percent: true, required: true },
  { key: 'avgSleepHours', label: 'Average sleep per night', unit: 'h', hint: 'Optional' },
]

const EMPTY = Object.fromEntries(FIELDS.map((f) => [f.key, '']))

/** Server values (fractions) back into what the form shows (percent fields as 0-100). */
function toForm(checkIn) {
  return Object.fromEntries(
    FIELDS.map((f) => {
      const v = checkIn?.[f.key]
      if (v == null) return [f.key, '']
      return [f.key, String(f.percent ? Math.round(v * 1000) / 10 : v)]
    }),
  )
}

/** Weekly check-in: the study-habit inputs the risk model needs. Prefilled from the last check-in. */
export default function CheckInForm({ courseId, onSubmit, busy, error, studySummary }) {
  const [values, setValues] = useState(EMPTY)
  const [lastDate, setLastDate] = useState(null)
  const [localError, setLocalError] = useState(null)
  const [prefillError, setPrefillError] = useState(false)

  useEffect(() => {
    courseApi
      .latestCheckIn(courseId)
      .then((c) => {
        if (!c) return
        setValues(toForm(c))
        setLastDate(c.createdAt)
      })
      .catch(() => setPrefillError(true))
  }, [courseId])

  function set(key, value) {
    setValues((v) => ({ ...v, [key]: value }))
  }

  function submit(e) {
    e.preventDefault()
    const body = {}
    for (const f of FIELDS) {
      const raw = String(values[f.key]).trim()
      if (raw === '') {
        if (f.required) return setLocalError(`Please fill in "${f.label}".`)
        body[f.key] = null
        continue
      }
      const n = Number(raw)
      if (!Number.isFinite(n) || n < 0) return setLocalError(`"${f.label}" must be a number of 0 or more.`)
      if (f.percent && n > 100) return setLocalError(`"${f.label}" must be between 0 and 100%.`)
      body[f.key] = f.percent ? n / 100 : f.integer ? Math.round(n) : n
    }
    setLocalError(null)
    onSubmit(body)
  }

  return (
    <form onSubmit={submit} className="rounded-xl border border-line bg-surface p-5" noValidate>
      <h3 className="font-semibold">Weekly check-in</h3>
      <p className="mt-1 text-sm text-ink-2">
        A few honest numbers about how this course is going. They're used only for your risk check.
        {lastDate && ` Filled in from your last check-in on ${new Date(lastDate).toLocaleDateString()}.`}
        {prefillError && " Your last answers couldn't be loaded, so the form starts empty."}
      </p>
      {studySummary?.totalSessions > 0 && (
        <div className="mt-3 flex flex-wrap items-center gap-2 rounded-md bg-accent-soft px-3 py-2 text-sm">
          <span className="flex-1">
            Your study log: {studySummary.avgWeeklyHours} h/week recently, {studySummary.totalSessions} sessions.
          </span>
          <button
            type="button"
            className="font-semibold text-accent-strong underline"
            onClick={() =>
              setValues((v) => ({
                ...v,
                avgWeeklyStudyHours: String(studySummary.avgWeeklyHours),
                studySessionsLogged: String(studySummary.totalSessions),
              }))
            }
          >
            Use these numbers
          </button>
        </div>
      )}
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        {FIELDS.map((f) => (
          <Field key={f.key} label={`${f.label}${f.unit ? ` (${f.unit})` : ''}`} hint={f.hint}>
            <input
              type="number"
              inputMode="decimal"
              min="0"
              step="any"
              className={inputClass}
              value={values[f.key]}
              onChange={(e) => set(f.key, e.target.value)}
            />
          </Field>
        ))}
      </div>
      <div className="mt-4 space-y-3">
        <ErrorBanner error={localError || error} />
        <Button type="submit" disabled={busy} className="w-full sm:w-auto">
          {busy ? 'Checking…' : 'Predict risk'}
        </Button>
      </div>
    </form>
  )
}
