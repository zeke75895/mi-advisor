import { useState } from 'react'
import { Link } from 'react-router'
import { courseApi } from '../api/client'
import { CATEGORY_LABELS } from '../lib/format'
import AiLabel from './AiLabel'
import { Button, ErrorBanner, inputClass } from './Field'

/**
 * Finds graded items in the uploaded syllabus and lets the student check, fix and add them.
 * Nothing is saved until they press "Add".
 */
export default function SyllabusItemsReview({ courseId }) {
  const [rows, setRows] = useState(null)
  const [meta, setMeta] = useState(null)
  const [finding, setFinding] = useState(false)
  const [adding, setAdding] = useState(false)
  const [error, setError] = useState(null)
  const [added, setAdded] = useState(null)

  async function find() {
    setFinding(true)
    setError(null)
    setAdded(null)
    try {
      const r = await courseApi.extractItems(courseId)
      setMeta(r)
      setRows(r.items.map((i) => ({ ...i, weight: String(i.weight), dueDate: i.dueDate ?? '', keep: true })))
    } catch (e) {
      setError(e.message)
    } finally {
      setFinding(false)
    }
  }

  const set = (i, k, v) => setRows((rs) => rs.map((r, j) => (j === i ? { ...r, [k]: v } : r)))
  const selected = rows?.filter((r) => r.keep) ?? []
  const total = Math.round(selected.reduce((sum, r) => sum + (Number(r.weight) || 0), 0) * 10) / 10

  async function addSelected() {
    setAdding(true)
    setError(null)
    let count = 0
    try {
      for (const r of selected) {
        await courseApi.addItem(courseId, {
          name: r.name.trim(),
          category: r.category,
          weight: Number(r.weight),
          pointsPossible: 100,
          dueDate: r.dueDate ? new Date(`${r.dueDate}T23:59:00`).toISOString() : null,
          isGraded: false,
        })
        count++
      }
      setAdded(count)
      setRows(null)
    } catch (e) {
      setError(`Added ${count} of ${selected.length}. Then: ${e.message}`)
    } finally {
      setAdding(false)
    }
  }

  if (added != null) {
    return (
      <div role="status" className="rounded-xl border border-good/50 bg-good/5 p-4 text-sm">
        <p className="font-semibold">✓ Added {added} graded item{added === 1 ? '' : 's'}</p>
        <p className="mt-1 text-ink-2">
          Enter scores as they come back and rate how confident you feel on the{' '}
          <Link to={`/courses/${courseId}`} className="font-semibold text-accent-strong underline">
            course page
          </Link>
          .
        </p>
      </div>
    )
  }

  if (!rows) {
    return (
      <div className="space-y-2 rounded-xl border border-line bg-surface p-4">
        <p className="text-sm">
          <span className="font-semibold">Is this a syllabus?</span> We can look for graded items and their weights, and you
          review them before anything is added.
        </p>
        <Button variant="secondary" onClick={find} disabled={finding}>
          {finding ? 'Reading the syllabus…' : 'Find graded items'}
        </Button>
        <ErrorBanner error={error} onRetry={find} />
      </div>
    )
  }

  return (
    <section className="space-y-3 rounded-xl border border-line bg-surface p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="font-semibold">Graded items found ({rows.length})</h2>
        <span className={`text-sm tabular-nums ${Math.abs(total - 100) > 0.5 ? 'font-semibold text-critical' : 'text-ink-2'}`}>
          Selected weights: {total}%
        </span>
      </div>
      {meta?.label ? (
        <AiLabel disclaimer="Read from your syllabus by AI. Check every name, weight and date before adding." />
      ) : (
        <p className="text-xs text-ink-2">Found by matching lines like "Midterm exam 25%". Check every row before adding.</p>
      )}
      {meta?.warnings?.map((w) => (
        <p key={w} className="rounded-md bg-warning/15 px-3 py-2 text-xs text-ink">
          ! {w}
        </p>
      ))}
      {rows.length > 0 && (
        <ul className="space-y-2">
          {rows.map((r, i) => (
            <li key={i} className={`grid gap-2 rounded-lg border border-line p-3 sm:grid-cols-[auto_2fr_1fr_5rem_9rem] sm:items-center ${r.keep ? '' : 'opacity-50'}`}>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={r.keep} onChange={(e) => set(i, 'keep', e.target.checked)} aria-label={`Add ${r.name}`} />
                <span className="sm:hidden">Add</span>
              </label>
              <input className={inputClass + ' mt-0'} value={r.name} onChange={(e) => set(i, 'name', e.target.value)} aria-label="Item name" />
              <select className={inputClass + ' mt-0'} value={r.category} onChange={(e) => set(i, 'category', e.target.value)} aria-label="Category">
                {Object.entries(CATEGORY_LABELS).map(([v, l]) => (
                  <option key={v} value={v}>
                    {l}
                  </option>
                ))}
              </select>
              <div className="flex items-center gap-1">
                <input type="number" min="0" max="100" step="any" className={inputClass + ' mt-0'} value={r.weight} onChange={(e) => set(i, 'weight', e.target.value)} aria-label="Weight percent" />
                <span className="text-sm text-ink-2">%</span>
              </div>
              <input type="date" className={inputClass + ' mt-0'} value={r.dueDate} onChange={(e) => set(i, 'dueDate', e.target.value)} aria-label="Due date" />
            </li>
          ))}
        </ul>
      )}
      <ErrorBanner error={error} />
      <div className="flex flex-wrap gap-2">
        <Button onClick={addSelected} disabled={adding || selected.length === 0 || selected.some((r) => !r.name.trim() || !(Number(r.weight) > 0))}>
          {adding ? 'Adding…' : `Add ${selected.length} item${selected.length === 1 ? '' : 's'}`}
        </Button>
        <Button variant="secondary" onClick={() => setRows(null)}>
          Cancel
        </Button>
      </div>
    </section>
  )
}
