import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import {
  LayoutDashboard, ListOrdered, MonitorDot, Users, History as HistoryIcon,
  Settings as SettingsIcon, LogOut, Headset, Menu, X,
} from 'lucide-react'
import { useAuth } from '../auth'

const links = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/queues', label: 'Filas', icon: ListOrdered },
  { to: '/counters', label: 'Guichês', icon: MonitorDot },
  { to: '/attendants', label: 'Atendentes', icon: Users },
  { to: '/history', label: 'Histórico', icon: HistoryIcon },
  { to: '/settings', label: 'Configurações', icon: SettingsIcon },
]

export default function AdminLayout() {
  const { user, logout } = useAuth()
  const nav = useNavigate()
  const [open, setOpen] = useState(false)

  return (
    <div className="admin-shell">
      <button className="hamburger" onClick={() => setOpen(!open)} aria-label="Menu">
        {open ? <X size={22} /> : <Menu size={22} />}
      </button>
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="brand">
          <ListOrdered size={20} /> QueueFlow
        </div>
        <nav>
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} onClick={() => setOpen(false)} className={({ isActive }) => `side-link ${isActive ? 'active' : ''}`}>
              <Icon size={18} /> {label}
            </NavLink>
          ))}
          {user?.role === 'ATTENDANT' && (
            <NavLink to="/attendant" onClick={() => setOpen(false)} className={({ isActive }) => `side-link ${isActive ? 'active' : ''}`}>
              <Headset size={18} /> Atendimento
            </NavLink>
          )}
        </nav>
        <button className="side-link logout" onClick={() => nav('/totem')}>
          Totem ↗
        </button>
        <div className="sidebar-footer">
          <span className="user-chip">{user?.name}</span>
          <button className="side-link logout" onClick={logout}>
            <LogOut size={18} /> Sair
          </button>
        </div>
      </aside>
      {open && <div className="backdrop" onClick={() => setOpen(false)} />}
      <main className="admin-main">
        <Outlet />
      </main>
    </div>
  )
}
