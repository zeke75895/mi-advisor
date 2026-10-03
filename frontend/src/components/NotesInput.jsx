import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { courseApi } from '../api/client'
import { Button, ErrorBanner, inputClass } from './Field'

const MIN = 100
const MAX = 100000
const AI_READS = 20000

/**
 * The course's notes (pasted, or extracted from an uploaded PDF), saved on the server and shared by
 * flashcards and the quiz. Edits are saved before generating.
 */
export default function NotesInput({ courseId, actionLabel, busy, error, onGenerate }) {
  const [saved, setSaved] = useState(null)
  const [text, setText] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(null)

  useEffect(() => {
    let cancelled = false
    courseApi
      .notes(courseId)
      .then((n) => {
        if (cancelled) return
        setSaved(n)
        setText(n?.content ?? '')
      })
      .catch((e) => !cancelled && setSaveError(e.message))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [courseId])

  const trimmed = text.trim()
  const valid = trimmed.length >= MIN && trimmed.length <= MAX
  const dirty = trimmed !== (saved?.content ?? '').trim()

  async function submit(e) {
    e.preventDefault()
    setSaveError(null)
    if (dirty) {
      setSaving(true)
      try {
        setSaved(await courseApi.saveNotes(courseId, trimmed))
      } catch (err) {
        setSaveError(err.message)
        return
      } finally {
        setSaving(false)
      }
    }
    onGenerate()
  }

  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border border-line bg-surface p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <label htmlFor="notes" className="font-semibold">
          Course notes
        </label>
        <Link to={`/upload?course=${courseId}`} className="text-sm font-semibold text-accent-strong underline">
          Upload a PDF instead
        </Link>
      </div>
      <p className="-mt-2 text-sm text-ink-2">
        {saved?.source === 'PDF'
          ? `From ${saved.fileName ?? 'your PDF'}. Edit below if you like.`
          : 'Paste lecture notes, a reading or a study guide. They are saved to this course.'}
      </p>
      <textarea
        id="notes"
        rows={8}
        className={`${inputClass} font-mono text-xs leading-relaxed`}
        value={text}
        disabled={loading}
        onChange={(e) => setText(e.target.value)}
        placeholder={loading ? 'Loading your notes…' : 'e.g. Integration by parts: ∫u dv = uv − ∫v du. Choose u using LIATE…'}
      />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className={`text-xs ${trimmed.length > MAX ? 'font-medium text-critical' : 'text-muted'}`}>
          {trimmed.length.toLocaleString()} characters
          {trimmed.length < MIN && ` · at least ${MIN} needed`}
          {trimmed.length > AI_READS && ` · the AI reads the first ${AI_READS.toLocaleString()}`}
          {dirty && trimmed.length > 0 && ' · unsaved changes'}
        </span>
        <Button type="submit" disabled={!valid || busy || saving || loading}>
          {saving ? 'Saving notes…' : busy ? 'Generating…' : actionLabel}
        </Button>
      </div>
      <ErrorBanner error={saveError || error} />
    </form>
  )
}
