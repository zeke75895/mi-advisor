import { useEffect, useState } from 'react'
import { courseApi, studyApi } from '../api/client'
import AiLabel from '../components/AiLabel'
import Field, { Button, ErrorBanner, Loading, inputClass } from '../components/Field'
import useAsync from '../lib/useAsync'
import { localDate } from '../lib/format'

export default function StudyPlanPage() {
  const courseList = useAsync(() => courseApi.list(), [])
  const saved = useAsync(() => studyApi.latestPlan(), [])
  const courses = courseList.data ?? []
  const [selected, setSelected] = useState({})
  const [hours, setHours] = useState(2)
  const [plan, setPlan] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (courseList.data) setSelected(Object.fromEntries(courseList.data.map((c) => [c.id, true])))
  }, [courseList.data])

  useEffect(() => {
    if (saved.data) setPlan(saved.data)
  }, [saved.data])

  async function generate(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const courseIds = courses.filter((c) => selected[c.id]).map((c) => c.id)
      // Send the browser's time zone so due dates land on the student's calendar days
      const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone
      setPlan(await studyApi.plan({ startDate: localDate(), hoursPerDay: hours, courseIds, timeZone }))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const noneSelected = courses.length > 0 && !courses.some((c) => selected[c.id])

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Study plan</h1>
        <p className="text-sm text-ink-2">
          A day-by-day plan for the next 7 days, built from your upcoming deadlines and the items you rated as low
          confidence.
        </p>
      </div>

      <form onSubmit={generate} className="space-y-4 rounded-xl border border-line bg-surface p-5">
        <fieldset>
          <legend className="text-sm font-medium">Courses</legend>
          {courseList.loading && <Loading label="Loading courses…" className="mt-2" />}
          <ErrorBanner error={courseList.error && `Couldn't load your courses: ${courseList.error}`} onRetry={courseList.reload} />
          {!courseList.loading && !courseList.error && courses.length === 0 && (
            <p className="mt-1 text-sm text-ink-2">Add a course from the dashboard first.</p>
          )}
          <div className="mt-2 flex flex-wrap gap-2">
            {courses.map((c) => (
              <label key={c.id} className="flex items-center gap-2 rounded-md border border-line px-3 py-1.5 text-sm">
                <input
                  type="checkbox"
                  checked={Boolean(selected[c.id])}
                  onChange={(e) => setSelected((s) => ({ ...s, [c.id]: e.target.checked }))}
                />
                {c.courseCode}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="max-w-xs">
          <Field label="Study time per day">
            <select className={inputClass} value={hours} onChange={(e) => setHours(Number(e.target.value))}>
              {[1, 2, 3, 4, 5, 6].map((h) => (
                <option key={h} value={h}>
                  {h} hour{h > 1 ? 's' : ''}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <ErrorBanner error={error} />
        <Button type="submit" disabled={busy || courses.length === 0 || noneSelected}>
          {busy ? 'Planning your week…' : plan ? 'Make a new plan' : 'Make my plan'}
        </Button>
      </form>

      {saved.loading && !plan && <Loading label="Loading your saved plan…" />}
      <ErrorBanner error={saved.error && `Couldn't load your saved plan: ${saved.error}`} onRetry={saved.reload} />

      {plan && (
        <section className="space-y-4">
          <AiLabel disclaimer={plan.disclaimer} />
          <div className="grid gap-4 sm:grid-cols-2">
            <Considered title="Deadlines in the next 2 weeks" items={plan.deadlinesConsidered} />
            <Considered title="Topics you feel unsure about" items={plan.weakTopicsConsidered} />
          </div>
          <ol className="space-y-3">
            {plan.days.map((day) => {
              const minutes = day.tasks.reduce((sum, t) => sum + t.minutes, 0)
              return (
                <li key={day.date} className="rounded-xl border border-line bg-surface p-4">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <h2 className="font-semibold">
                      {day.dayOfWeek}{' '}
                      <span className="font-normal text-ink-2">
                        {new Date(`${day.date}T12:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                      </span>
                    </h2>
                    <span className="text-xs text-muted tabular-nums">{minutes ? `${minutes} min` : 'Rest day'}</span>
                  </div>
                  {day.focus && <p className="mt-1 text-sm text-ink-2">{day.focus}</p>}
                  {day.tasks.length > 0 && (
                    <ul className="mt-3 space-y-2">
                      {day.tasks.map((t, i) => (
                        <li key={i} className="flex items-start gap-3 text-sm">
                          <span className="w-14 shrink-0 rounded bg-accent-soft px-1.5 py-0.5 text-center text-xs font-semibold tabular-nums">
                            {t.minutes}m
                          </span>
                          <span className="flex-1">
                            {t.course && <span className="font-semibold">{t.course}: </span>}
                            {t.task}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              )
            })}
          </ol>
        </section>
      )}
    </div>
  )
}

function Considered({ title, items }) {
  return (
    <div className="rounded-xl border border-line bg-surface p-4">
      <h2 className="text-sm font-semibold">{title}</h2>
      {items.length ? (
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-ink-2">
          {items.map((i) => (
            <li key={i}>{i}</li>
          ))}
        </ul>
      ) : (
        <p className="mt-2 text-sm text-ink-2">None</p>
      )}
    </div>
  )
}
