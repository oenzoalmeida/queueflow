import { useState } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { usePageTitle } from '../usePageTitle'

export default function Login() {
  usePageTitle('Entrar')
  const { login, user } = useAuth()
  const nav = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (user) return <Navigate to={user.mustChangePassword ? '/trocar-senha' : user.role === 'ATTENDANT' ? '/attendant' : '/dashboard'} replace />

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const u = await login(email.trim(), password)
      nav(u.mustChangePassword ? '/trocar-senha' : u.role === 'ATTENDANT' ? '/attendant' : '/dashboard', { replace: true })
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-shell">
        <aside className="login-brand">
          <span className="login-brand-ticket" aria-hidden="true">A012</span>
          <h1>QueueFlow</h1>
          <p className="login-brand-tag">Gerenciamento de filas e senhas</p>
          <ul className="login-brand-list">
            <li><strong>Senhas em segundos</strong><span>Totem simples para o cliente</span></li>
            <li><strong>Painel em tempo real</strong><span>Chamadas ao vivo na TV</span></li>
            <li><strong>Prioridade respeitada</strong><span>Atendimento justo para todos</span></li>
          </ul>
        </aside>
        <form className="login-card" onSubmit={submit}>
          <h2>Entrar na conta</h2>
          <p className="muted">Acesse o painel do seu estabelecimento</p>
          {error && <div className="alert error">{error}</div>}
          <label htmlFor="login-email">Email</label>
          <input id="login-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="voce@empresa.com" required autoFocus />
          <label htmlFor="login-password">Senha</label>
          <input id="login-password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" required />
          <button className="btn primary" disabled={loading}>
            {loading ? 'Entrando…' : 'Entrar'}
          </button>
          <p className="login-legal muted">
            <a href="/termos">Termos de Uso</a> · <a href="/privacidade">Política de Privacidade</a>
          </p>
        </form>
      </div>
    </div>
  )
}
