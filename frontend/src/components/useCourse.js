import { courseApi } from '../api/client'
import useAsync from '../lib/useAsync'

/** Loads one of the user's courses (there's no single-course endpoint, so filter the list). */
export default function useCourse(courseId) {
  const { data, error, loading } = useAsync(async () => {
    const found = (await courseApi.list()).find((c) => String(c.id) === String(courseId))
    if (!found) throw new Error('Course not found.')
    return found
  }, [courseId])
  return { course: data, error, loading }
}
