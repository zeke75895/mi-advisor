import { useState } from 'react'
import { courseApi } from '../api/client'
import { CATEGORY_LABELS } from '../lib/format'
import Field, { Button, ErrorBanner, inputClass } from './Field'

const EMPTY = { name: '', category: 'EXAM', weight: '', pointsPossible: '100', pointsEarned: '', dueDate: '', isGraded: false }

export default function AddItemForm({ courseId, onAdded }) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }))

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await courseApi.addItem(courseId, {
        name: form.name,
        category: form.category,
        weight: form.weight === '' ? null : Number(form.weight),
        pointsPossible: form.pointsPossible === '' ? null : Number(form.pointsPossible),
        pointsEarned: form.isGraded && form.pointsEarned !== '' ? Number(form.pointsEarned) : null,
        dueDate: form.dueDate ? new Date(`${form.dueDate}T23:59:00`).toISOString() : null,
        isGraded: form.isGraded,
      })
      setForm(EMPTY)
      setOpen(false)
      onAdded()
    } catch (err) {
      const fields = Object.entries(err.fieldErrors ?? {}).map(([k, v]) => `${k}: ${v}`)
      setError(fields.length ? fields.join(' · ') : err.message)
    } finally {
      setBusy(false)
    }
  }

  if (!open) {
    return (
      <Button variant="secondary" onClick={() => setOpen(true)} className="w-full">
        + Add graded item
      </Button>
    )
  }
  return (
    <form onSubmit={submit} className="space-y-3 rounded-lg border border-line bg-surface p-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Name">
          <input className={inputClass} value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="Midterm" required />
        </Field>
        <Field label="Category">
          <select className={inputClass} value={form.category} onChange={(e) => set('category', e.target.value)}>
            {Object.entries(CATEGORY_LABELS).map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Weight (% of final grade)">
          <input type="number" min="0" max="100" step="any" className={inputClass} value={form.weight} onChange={(e) => set('weight', e.target.value)} required />
        </Field>
        <Field label="Due date">
          <input type="date" className={inputClass} value={form.dueDate} onChange={(e) => set('dueDate', e.target.value)} />
        </Field>
      </div>
      <label className="flex items-center gap-2 text-sm font-medium">
        <input type="checkbox" checked={form.isGraded} onChange={(e) => set('isGraded', e.target.checked)} />
        Already graded
      </label>
      {form.isGraded && (
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Points earned">
            <input type="number" min="0" step="any" className={inputClass} value={form.pointsEarned} onChange={(e) => set('pointsEarned', e.target.value)} required />
          </Field>
          <Field label="Points possible">
            <input type="number" min="0" step="any" className={inputClass} value={form.pointsPossible} onChange={(e) => set('pointsPossible', e.target.value)} required />
          </Field>
        </div>
      )}
      <ErrorBanner error={error} />
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Adding…' : 'Add item'}
        </Button>
        <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
