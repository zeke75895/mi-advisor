import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { courseApi } from '../api/client'
import AddItemForm from '../components/AddItemForm'
import CheckInForm from '../components/CheckInForm'
import { ErrorBanner, Loading } from '../components/Field'
import GradeMeter from '../components/GradeMeter'
import ItemRow from '../components/ItemRow'
import RecommendationCard from '../components/RecommendationCard'
import RiskCard from '../components/RiskCard'

export default function CourseDetailPage() {
  const { courseId } = useParams()
  const [course, setCourse] = useState(null)
  const [items, setItems] = useState(null)
  const [projection, setProjection] = useState(null)
  const [recommendation, setRecommendation] = useState(null)
  const [risk, setRisk] = useState(null)
  const [stale, setStale] = useState(false)
  const [error, setError] = useState(null)
  const [predicting, setPredicting] = useState(false)
  const [predictError, setPredictError] = useState(null)
  const [refreshing, setRefreshing] = useState(false)

  const refreshProjection = useCallback(
    () =>
      courseApi
        .projection(courseId)
        .then(setProjection)
        .catch((e) => setError(`Couldn't update the projected grade: ${e.message}`)),
    [courseId],
  )
  const refreshItems = useCallback(
    () =>
      courseApi
        .items(courseId)
        .then(setItems)
        .catch((e) => setError(`Couldn't reload graded items: ${e.message}`)),
    [courseId],
  )

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const [courses, itemList, proj, rec, latestRisk] = await Promise.all([
          courseApi.list(),
          courseApi.items(courseId),
          courseApi.projection(courseId),
          courseApi.recommendation(courseId),
          courseApi.latestRisk(courseId),
        ])
        if (cancelled) return
        const found = courses.find((c) => String(c.id) === courseId)
        if (!found) throw new Error('Course not found.')
        setCourse(found)
        setItems(itemList)
        setProjection(proj)
        setRecommendation(rec)
        setRisk(latestRisk)
      } catch (e) {
        if (!cancelled) setError(e.message)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [courseId])

  // Ratings update the projection right away. The recommendation (which calls Gemini) only refreshes
  // when the student asks, so dragging a slider doesn't burn API quota.
  function onRated() {
    refreshProjection()
    if (recommendation) setStale(true)
  }

  async function refreshRecommendation() {
    setRefreshing(true)
    try {
      setRecommendation(await courseApi.recommendation(courseId))
      setStale(false)
    } catch (e) {
      setError(`Couldn't update the recommendation: ${e.message}`)
    } finally {
      setRefreshing(false)
    }
  }

  async function predict(features) {
    setPredicting(true)
    setPredictError(null)
    try {
      setRisk(await courseApi.predict(courseId, features))
      await refreshRecommendation()
    } catch (e) {
      setPredictError(e.message)
    } finally {
      setPredicting(false)
    }
  }

  if (error && !course) {
    return (
      <div className="space-y-4">
        <ErrorBanner error={error} />
        <Link to="/" className="text-sm font-semibold text-accent-strong underline">
          Back to dashboard
        </Link>
      </div>
    )
  }
  if (!course) return <Loading label="Loading course…" />

  return (
    <div className="space-y-6">
      <div>
        <Link to="/" className="text-sm text-ink-2 hover:underline">
          ← All courses
        </Link>
        <p className="mt-2 text-xs font-semibold uppercase tracking-wide text-muted">
          {course.courseCode}
          {course.semester && ` · ${course.semester}`}
        </p>
        <h1 className="text-2xl font-bold">{course.courseName}</h1>
      </div>
      <ErrorBanner error={error} onRetry={() => setError(null)} />

      <div className="grid gap-6 lg:grid-cols-5">
        <section className="space-y-3 lg:col-span-3">
          <h2 className="text-lg font-semibold">Graded items</h2>
          {items?.length === 0 && (
            <p className="text-sm text-ink-2">No items yet. Add the exams, homework, quizzes and projects from your syllabus.</p>
          )}
          <ul className="space-y-3">
            {items?.map((item) => (
              <ItemRow
                key={item.id}
                item={item}
                onRated={onRated}
                onChanged={() => {
                  refreshItems()
                  refreshProjection()
                  if (recommendation) setStale(true)
                }}
              />
            ))}
          </ul>
          <AddItemForm
            courseId={courseId}
            onAdded={() => {
              refreshItems()
              refreshProjection()
              if (recommendation) setStale(true)
            }}
          />
        </section>

        <aside className="space-y-6 lg:col-span-2">
          <section className="rounded-xl border border-line bg-surface p-5">
            <h2 className="font-semibold">Projected final grade</h2>
            <div className="mt-3">
              <GradeMeter projection={projection} />
            </div>
          </section>
          {risk && <RiskCard result={risk} />}
        </aside>
      </div>

      <section className="rounded-xl border border-line bg-surface p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="font-semibold">Study tools</h2>
            <p className="text-sm text-ink-2">Turn your notes into AI-generated flashcards or a practice quiz.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link to={`/courses/${courseId}/flashcards`} className="rounded-md border border-line px-4 py-2 text-sm font-semibold hover:bg-line/40">
              Flashcards
            </Link>
            <Link to={`/courses/${courseId}/quiz`} className="rounded-md border border-line px-4 py-2 text-sm font-semibold hover:bg-line/40">
              Practice quiz
            </Link>
          </div>
        </div>
      </section>

      {recommendation && (
        <RecommendationCard rec={recommendation} stale={stale} onRefresh={refreshRecommendation} refreshing={refreshing} />
      )}

      <CheckInForm courseId={courseId} onSubmit={predict} busy={predicting} error={predictError} />
    </div>
  )
}
