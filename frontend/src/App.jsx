import { Navigate, Route, Routes, useLocation } from 'react-router'
import { useAuth } from './auth/AuthContext'
import Layout from './components/Layout'
import CourseDetailPage from './pages/CourseDetailPage'
import DashboardPage from './pages/DashboardPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
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
        <Route path="upload" element={<UploadPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
