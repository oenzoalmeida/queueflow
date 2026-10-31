import { useState } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { usePageTitle } from '../usePageTitle'

export default function ChangePassword() {
  const { user, changePassword, logout } = useAuth()
  const nav = useNavigate()
  usePageTitle('Trocar senha')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (!user) return <Navigate to="/login" replace />
  if (!user.mustChangePassword) return <Navigate to={user.role === 'ATTENDANT' ? '/attendant' : '/dashboard'} replace />

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (newPassword !== confirm) {
      setError('A confirmação não confere com a nova senha.')
      return
    }
    setLoading(true)
    try {
      const u = await changePassword(currentPassword, newPassword)
      nav(u.role === 'ATTENDANT' ? '/attendant' : '/dashboard', { replace: true })
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={submit}>
        <h1>QueueFlow</h1>
        <p className="muted">Troca de senha obrigatória no primeiro acesso</p>
        {error && <div className="alert error">{error}</div>}
        <label htmlFor="cp-current">Senha temporária</label>
        <input id="cp-current" type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} required autoFocus />
        <label htmlFor="cp-new">Nova senha</label>
        <input id="cp-new" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} minLength={6} maxLength={72} required />
        <label htmlFor="cp-confirm">Confirmar nova senha</label>
        <input id="cp-confirm" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        <button className="btn primary" disabled={loading}>
          {loading ? 'Salvando…' : 'Definir nova senha'}
        </button>
        <button type="button" className="btn" onClick={() => logout()}>
          Sair
        </button>
      </form>
    </div>
  )
}
