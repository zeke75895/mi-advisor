import { useEffect, useState } from 'react'
import { courseApi } from '../api/client'

/** Loads one of the user's courses (there's no single-course endpoint, so filter the list). */
export default function useCourse(courseId) {
  const [course, setCourse] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => {
    let cancelled = false
    courseApi
      .list()
      .then((list) => {
        if (cancelled) return
        const found = list.find((c) => String(c.id) === String(courseId))
        found ? setCourse(found) : setError('Course not found.')
      })
      .catch((e) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [courseId])
  return { course, error }
}
