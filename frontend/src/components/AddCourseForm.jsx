import { useState } from 'react'
import { courseApi } from '../api/client'
import Field, { Button, ErrorBanner, inputClass } from './Field'

const EMPTY = { courseCode: '', courseName: '', creditHours: '3', semester: 'Fall 2026' }

export default function AddCourseForm({ onAdded, startOpen = false }) {
  const [open, setOpen] = useState(startOpen)
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }))

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const course = await courseApi.create({
        ...form,
        creditHours: form.creditHours === '' ? null : Number(form.creditHours),
      })
      setForm(EMPTY)
      setOpen(false)
      onAdded(course)
    } catch (err) {
      const fields = Object.entries(err.fieldErrors ?? {}).map(([k, v]) => `${k}: ${v}`)
      setError(fields.length ? fields.join(' · ') : err.message)
    } finally {
      setBusy(false)
    }
  }

  if (!open) {
    return <Button onClick={() => setOpen(true)}>+ Add course</Button>
  }
  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border border-line bg-surface p-5">
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Course code">
          <input className={inputClass} value={form.courseCode} onChange={(e) => set('courseCode', e.target.value)} placeholder="MA 241" required />
        </Field>
        <Field label="Course name">
          <input className={inputClass} value={form.courseName} onChange={(e) => set('courseName', e.target.value)} placeholder="Calculus II" required />
        </Field>
        <Field label="Credit hours">
          <input type="number" min="0" max="12" className={inputClass} value={form.creditHours} onChange={(e) => set('creditHours', e.target.value)} />
        </Field>
        <Field label="Semester">
          <input className={inputClass} value={form.semester} onChange={(e) => set('semester', e.target.value)} />
        </Field>
      </div>
      <ErrorBanner error={error} />
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Adding…' : 'Add course'}
        </Button>
        <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
