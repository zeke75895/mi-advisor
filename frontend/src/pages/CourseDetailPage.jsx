import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { courseApi } from '../api/client'
import AddItemForm from '../components/AddItemForm'
import CheckInForm from '../components/CheckInForm'
import { ErrorBanner } from '../components/Field'
import GradeMeter from '../components/GradeMeter'
import ItemRow from '../components/ItemRow'
import RecommendationCard from '../components/RecommendationCard'
import RiskCard from '../components/RiskCard'
import { RiskBadge } from '../components/StatusBadge'
import { loadLocal, riskLevel, saveLocal } from '../lib/format'

export default function CourseDetailPage() {
  const { courseId } = useParams()
  const riskKey = `cc_risk_${courseId}`
  const [course, setCourse] = useState(null)
  const [items, setItems] = useState(null)
  const [projection, setProjection] = useState(null)
  const [recommendation, setRecommendation] = useState(null)
  const [risk, setRisk] = useState(() => loadLocal(riskKey, null))
  const [stale, setStale] = useState(false)
  const [error, setError] = useState(null)
  const [predicting, setPredicting] = useState(false)
  const [predictError, setPredictError] = useState(null)
  const [refreshing, setRefreshing] = useState(false)

  const refreshProjection = useCallback(() => courseApi.projection(courseId).then(setProjection), [courseId])
  const refreshItems = useCallback(() => courseApi.items(courseId).then(setItems), [courseId])

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const [courses, itemList, proj, rec] = await Promise.all([
          courseApi.list(),
          courseApi.items(courseId),
          courseApi.projection(courseId),
          courseApi.recommendation(courseId),
        ])
        if (cancelled) return
        const found = courses.find((c) => String(c.id) === courseId)
        if (!found) throw new Error('Course not found.')
        setCourse(found)
        setItems(itemList)
        setProjection(proj)
        setRecommendation(rec)
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
      setError(e.message)
    } finally {
      setRefreshing(false)
    }
  }

  async function predict(features) {
    setPredicting(true)
    setPredictError(null)
    try {
      const result = await courseApi.predict(courseId, features)
      setRisk(result)
      saveLocal(riskKey, result)
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
  if (!course) return <p className="text-ink-2">Loading…</p>

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
      <ErrorBanner error={error} />

      <div className="grid gap-6 lg:grid-cols-5">
        <section className="space-y-3 lg:col-span-3">
          <h2 className="text-lg font-semibold">Graded items</h2>
          {items?.length === 0 && (
            <p className="text-sm text-ink-2">No items yet. Add the exams, homework, quizzes and projects from your syllabus.</p>
          )}
          <ul className="space-y-3">
            {items?.map((item) => (
              <ItemRow key={item.id} item={item} onRated={onRated} />
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
          {risk ? (
            <RiskCard result={risk} />
          ) : (
            recommendation && (
              // Full predict details are kept on the device that ran the check; elsewhere show the level only
              <section className="rounded-xl border border-line bg-surface p-5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 className="font-semibold">Risk check</h3>
                  <RiskBadge level={riskLevel(recommendation.signals.modelRisk)} />
                </div>
                <p className="mt-2 text-sm text-ink-2">
                  Last checked {new Date(recommendation.riskComputedAt).toLocaleDateString()}. Submit the weekly check-in
                  below to see what drove this result.
                </p>
              </section>
            )
          )}
        </aside>
      </div>

      {recommendation && (
        <RecommendationCard rec={recommendation} stale={stale} onRefresh={refreshRecommendation} refreshing={refreshing} />
      )}

      <CheckInForm courseId={courseId} onSubmit={predict} busy={predicting} error={predictError} />
    </div>
  )
}
