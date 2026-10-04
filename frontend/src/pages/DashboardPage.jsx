import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { courseApi, sessionApi } from '../api/client'
import useAsync from '../lib/useAsync'
import AddCourseForm from '../components/AddCourseForm'
import { ErrorBanner, Loading } from '../components/Field'
import GradeMeter from '../components/GradeMeter'
import RaiPanel from '../components/RaiPanel'
import StatusBadge, { RiskBadge } from '../components/StatusBadge'
import { RECOMMENDATION_TONE, hoursMinutes, localDate, riskLevel } from '../lib/format'

async function loadCourse(course) {
  const [projection, recommendation] = await Promise.allSettled([
    courseApi.projection(course.id),
    courseApi.recommendation(course.id),
  ])
  return {
    ...course,
    projection: projection.value ?? null,
    recommendation: recommendation.value ?? null,
    loadError: projection.status === 'rejected' || recommendation.status === 'rejected',
  }
}

export default function DashboardPage() {
  const [courses, setCourses] = useState(null)
  const [error, setError] = useState(null)
  const study = useAsync(() => sessionApi.summary(localDate()), [])
  const minutesByCourse = Object.fromEntries((study.data?.courses ?? []).map((c) => [c.courseId, c.last7DaysMinutes]))

  const load = useCallback(async () => {
    try {
      setError(null)
      const list = await courseApi.list()
      setCourses(await Promise.all(list.map(loadCourse)))
    } catch (e) {
      setError(e.message)
      setCourses([])
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">Your courses</h1>
          <p className="text-sm text-ink-2">Risk, projected grade and our recommendation for each course.</p>
          {study.data && (
            <p className="mt-1 text-sm font-medium">
              Studied in the last 7 days: {hoursMinutes(study.data.last7DaysMinutes)}
            </p>
          )}
        </div>
        {courses?.length > 0 && <AddCourseForm onAdded={load} />}
      </div>

      <ErrorBanner error={error} onRetry={load} />

      {courses === null && <Loading label="Loading your courses…" />}

      {courses?.length === 0 && !error && (
        <div className="rounded-xl border border-dashed border-line bg-surface p-6 text-center">
          <p className="font-semibold">No courses yet</p>
          <p className="mt-1 text-sm text-ink-2">Add your first course, then its graded items from the syllabus.</p>
          <div className="mx-auto mt-4 max-w-xl text-left">
            <AddCourseForm onAdded={load} startOpen />
          </div>
        </div>
      )}

      <ul className="grid gap-4 sm:grid-cols-2">
        {courses?.map((c) => (
          <li key={c.id}>
            <CourseCard course={c} studiedMinutes={minutesByCourse[c.id]} />
          </li>
        ))}
      </ul>
    </div>
  )
}

function CourseCard({ course, studiedMinutes }) {
  const rec = course.recommendation
  return (
    <Link
      to={`/courses/${course.id}`}
      className="block h-full rounded-xl border border-line bg-surface p-5 transition hover:border-accent hover:shadow-sm focus:outline-none focus:ring-2 focus:ring-accent/40"
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-muted">{course.courseCode}</p>
          <h2 className="text-lg font-semibold">{course.courseName}</h2>
        </div>
        <RiskBadge level={rec ? riskLevel(rec.signals.modelRisk) : null} />
      </div>
      <div className="mt-4">
        <GradeMeter projection={course.projection} compact />
      </div>
      {studiedMinutes != null && (
        <p className="mt-2 text-xs text-ink-2">Studied in the last 7 days: {hoursMinutes(studiedMinutes)}</p>
      )}
      <div className="mt-4 border-t border-line pt-3">
        {course.loadError ? (
          <p className="text-sm text-ink-2">Couldn't load this course's latest numbers. Open it to try again.</p>
        ) : rec ? (
          <div className="space-y-2">
            <StatusBadge tone={RECOMMENDATION_TONE[rec.recommendation]}>{rec.headline}</StatusBadge>
            <RaiPanel compact />
          </div>
        ) : (
          <p className="text-sm text-ink-2">No risk check yet. Open the course to run one.</p>
        )}
      </div>
    </Link>
  )
}
