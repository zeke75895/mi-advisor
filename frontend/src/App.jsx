import { Navigate, Route, Routes, useLocation } from 'react-router'
import { useAuth } from './auth/AuthContext'
import Layout from './components/Layout'
import CourseDetailPage from './pages/CourseDetailPage'
import DashboardPage from './pages/DashboardPage'
import FlashcardsPage from './pages/FlashcardsPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import QuizPage from './pages/QuizPage'
import StudyPlanPage from './pages/StudyPlanPage'
import UploadPage from './pages/UploadPage'

function RequireAuth({ children }) {
  const { signedIn } = useAuth()
  const location = useLocation()
  return signedIn ? children : <Navigate to="/login" replace state={{ from: location.pathname }} />
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="courses/:courseId" element={<CourseDetailPage />} />
        <Route path="courses/:courseId/flashcards" element={<FlashcardsPage />} />
        <Route path="courses/:courseId/quiz" element={<QuizPage />} />
        <Route path="study-plan" element={<StudyPlanPage />} />
        <Route path="upload" element={<UploadPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
