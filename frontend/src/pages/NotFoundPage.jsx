import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <div className="py-16 text-center">
      <h1 className="text-2xl font-bold">Page not found</h1>
      <Link to="/" className="mt-4 inline-block font-semibold text-accent-strong underline">
        Back to your courses
      </Link>
    </div>
  )
}
