import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { courseApi } from '../api/client'
import { Button, ErrorBanner, inputClass } from '../components/Field'

const MAX_BYTES = 10 * 1024 * 1024

export default function UploadPage() {
  const [courses, setCourses] = useState([])
  const [courseId, setCourseId] = useState('')
  const [file, setFile] = useState(null)
  const [dragging, setDragging] = useState(false)
  const [error, setError] = useState(null)
  const [done, setDone] = useState(false)
  const input = useRef(null)

  useEffect(() => {
    courseApi.list().then(setCourses).catch(() => setCourses([]))
  }, [])

  function pick(f) {
    setDone(false)
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

  function upload() {
    // Parsing is stubbed for now: we accept the file and confirm, nothing is sent to the server yet.
    setDone(true)
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Upload a syllabus</h1>
        <p className="text-sm text-ink-2">Drop in your syllabus PDF and we'll pull out the graded items and deadlines.</p>
      </div>

      <label className="block text-sm font-medium">
        Course (optional)
        <select className={inputClass} value={courseId} onChange={(e) => setCourseId(e.target.value)}>
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
        <p className="mt-2 font-semibold">{file ? file.name : 'Drag your syllabus PDF here'}</p>
        <p className="text-sm text-ink-2">
          {file ? `${(file.size / 1024).toFixed(0)} KB · click to choose a different file` : 'or click to browse · PDF up to 10 MB'}
        </p>
        <input ref={input} type="file" accept="application/pdf,.pdf" className="hidden" onChange={(e) => pick(e.target.files?.[0])} />
      </div>

      <ErrorBanner error={error} />

      {file && !done && (
        <Button onClick={upload} className="w-full sm:w-auto">
          Upload syllabus
        </Button>
      )}

      {done && (
        <div role="status" className="rounded-xl border border-good/50 bg-good/5 p-4">
          <p className="font-semibold">✓ Syllabus received: {file.name}</p>
          <p className="mt-1 text-sm text-ink-2">
            Automatic parsing is coming soon. For now, add the graded items by hand
            {courseId ? (
              <>
                {' '}on{' '}
                <Link to={`/courses/${courseId}`} className="font-semibold text-accent-strong underline">
                  the course page
                </Link>
              </>
            ) : (
              ' on the course page'
            )}
            .
          </p>
        </div>
      )}
    </div>
  )
}
