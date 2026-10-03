import { Link } from 'react-router'

export default function StudyHeader({ course, courseId, title }) {
  return (
    <div>
      <Link to={`/courses/${courseId}`} className="text-sm text-ink-2 hover:underline">
        ← {course ? `${course.courseCode} ${course.courseName}` : 'Back to course'}
      </Link>
      <h1 className="mt-2 text-2xl font-bold">{title}</h1>
    </div>
  )
}
