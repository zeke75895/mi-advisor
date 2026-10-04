import { useRef, useState } from 'react'
import useAsync from '../lib/useAsync'
import { Link, useSearchParams } from 'react-router'
import { courseApi } from '../api/client'
import { Button, ErrorBanner, Loading, inputClass } from '../components/Field'
import SyllabusItemsReview from '../components/SyllabusItemsReview'

const MAX_BYTES = 10 * 1024 * 1024

export default function UploadPage() {
  const [params] = useSearchParams()
  const courseList = useAsync(() => courseApi.list(), [])
  const courses = courseList.data ?? []
  const [courseId, setCourseId] = useState(params.get('course') ?? '')
  const [file, setFile] = useState(null)
  const [dragging, setDragging] = useState(false)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)
  const input = useRef(null)

  function pick(f) {
    setResult(null)
    if (!f) return
    if (f.type !== 'application/pdf' && !f.name.toLowerCase().endsWith('.pdf')) {
      setFile(null)
      return setError('Please choose a PDF file.')
    }
    if (f.size > MAX_BYTES) {
      setFile(null)
      return setError('That file is over 10 MB.')
    }
    setError(null)
    setFile(f)
  }

  async function upload() {
    setBusy(true)
    setError(null)
    try {
      setResult(await courseApi.uploadNotesPdf(courseId, file))
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Upload a syllabus or notes</h1>
        <p className="text-sm text-ink-2">
          We pull the text out of your PDF and save it as the course's notes, ready for flashcards and practice
          quizzes.
        </p>
      </div>

      {courseList.loading && <Loading label="Loading your courses…" />}
      <ErrorBanner error={courseList.error && `Couldn't load your courses: ${courseList.error}`} onRetry={courseList.reload} />
      {!courseList.loading && !courseList.error && courses.length === 0 && (
        <p className="text-sm text-ink-2">
          Add a course on the <Link to="/" className="font-semibold text-accent-strong underline">dashboard</Link> first.
        </p>
      )}
      <label className="block text-sm font-medium">
        Course
        <select
          className={inputClass}
          value={courseId}
          onChange={(e) => {
            setCourseId(e.target.value)
            setResult(null)
          }}
        >
          <option value="">Choose a course…</option>
          {courses.map((c) => (
            <option key={c.id} value={c.id}>
              {c.courseCode} · {c.courseName}
            </option>
          ))}
        </select>
      </label>

      <div
        role="button"
        tabIndex={0}
        onClick={() => input.current?.click()}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragging(false)
          pick(e.dataTransfer.files?.[0])
        }}
        className={`flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-12 text-center transition focus:outline-none focus:ring-2 focus:ring-accent/40 ${
          dragging ? 'border-accent bg-accent-soft' : 'border-line bg-surface hover:border-accent'
        }`}
      >
        <span aria-hidden className="text-3xl">📄</span>
        <p className="mt-2 font-semibold">{file ? file.name : 'Drag your PDF here'}</p>
        <p className="text-sm text-ink-2">
          {file ? `${(file.size / 1024).toFixed(0)} KB · click to choose a different file` : 'or click to browse · PDF up to 10 MB'}
        </p>
        <input ref={input} type="file" accept="application/pdf,.pdf" className="hidden" onChange={(e) => pick(e.target.files?.[0])} />
      </div>

      <ErrorBanner error={error} />

      {file && !result && (
        <div className="flex flex-wrap items-center gap-3">
          <Button onClick={upload} disabled={!courseId || busy}>
            {busy ? 'Reading your PDF…' : 'Upload and extract text'}
          </Button>
          {!courseId && <span className="text-sm text-ink-2">Choose a course first.</span>}
        </div>
      )}

      {result && (
        <div role="status" className="space-y-3 rounded-xl border border-good/50 bg-good/5 p-4">
          <div>
            <p className="font-semibold">✓ Saved {result.length.toLocaleString()} characters from {result.fileName}</p>
            <p className="mt-1 text-sm text-ink-2">
              These are now this course's notes.
              {result.truncatedForAi && ' The study tools read the first 20,000 characters.'}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link to={`/courses/${courseId}/flashcards`} className="rounded-md bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-strong">
              Make flashcards
            </Link>
            <Link to={`/courses/${courseId}/quiz`} className="rounded-md border border-line bg-surface px-4 py-2 text-sm font-semibold hover:bg-line/40">
              Make a practice quiz
            </Link>
            <Link to={`/courses/${courseId}`} className="rounded-md px-4 py-2 text-sm font-semibold text-accent-strong underline">
              Course page
            </Link>
          </div>
          <details className="text-sm">
            <summary className="cursor-pointer text-ink-2">Preview extracted text</summary>
            <pre className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap rounded bg-surface p-3 text-xs">{result.content.slice(0, 1500)}</pre>
          </details>
        </div>
      )}

      {result && <SyllabusItemsReview key={`${courseId}-${result.updatedAt}`} courseId={courseId} />}
    </div>
  )
}
