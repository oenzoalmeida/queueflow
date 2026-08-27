import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Queues from './pages/Queues'
import Counters from './pages/Counters'
import Attendants from './pages/Attendants'
import History from './pages/History'
import Settings from './pages/Settings'
import AttendantScreen from './pages/AttendantScreen'
import Totem from './pages/Totem'
import Display from './pages/Display'
import AdminLayout from './components/AdminLayout'

function RequireRole({ role, children }: { role: 'ADMIN' | 'ATTENDANT' | 'ANY'; children: React.ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (role === 'ADMIN' && user.role !== 'ADMIN') return <Navigate to="/attendant" replace />
  return <>{children}</>
}

export default function App() {
  const { user } = useAuth()
  return (
    <Routes>
      <Route path="/" element={<Navigate to={user?.role === 'ATTENDANT' ? '/attendant' : user ? '/dashboard' : '/login'} replace />} />
      <Route path="/login" element={<Login />} />

      <Route element={<RequireRole role="ADMIN"><AdminLayout /></RequireRole>}>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/queues" element={<Queues />} />
        <Route path="/counters" element={<Counters />} />
        <Route path="/attendants" element={<Attendants />} />
        <Route path="/history" element={<History />} />
        <Route path="/settings" element={<Settings />} />
      </Route>

      <Route
        path="/attendant"
        element={
          <RequireRole role="ANY">
            <AttendantScreen />
          </RequireRole>
        }
      />

      <Route path="/totem" element={<Totem />} />
      <Route path="/display" element={<Display />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
