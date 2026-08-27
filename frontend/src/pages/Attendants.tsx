import { useEffect, useState } from 'react'
import { api, errMessage } from '../api/client'
import type { UserDTO } from '../types'

export default function Attendants() {
  const [users, setUsers] = useState<UserDTO[]>([])
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [okMsg, setOkMsg] = useState<string | null>(null)

  async function load() {
    setUsers((await api.get('/users')).data)
  }
  useEffect(() => { load().catch(() => setError('Falha ao carregar atendentes.')) }, [])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null); setOkMsg(null); setLoading(true)
    try {
      await api.post('/users', { name, email: email.trim(), password })
      setName(''); setEmail(''); setPassword('')
      setOkMsg('Atendente cadastrado com sucesso.')
      await load()
    } catch (err) {
      setError(errMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const toggleActive = async (u: UserDTO) => {
    setError(null)
    try {
      await api.put(`/users/${u.id}`, { active: !u.active })
      await load()
    } catch (err) {
      setError(errMessage(err))
    }
  }

  return (
    <>
      <h2>Atendentes</h2>
      <section className="card">
        <h3>Novo atendente</h3>
        {error && <div className="alert error">{error}</div>}
        {okMsg && <div className="alert success">{okMsg}</div>}
        <form className="grid-form" onSubmit={submit}>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Nome completo" required maxLength={120} />
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" required />
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Senha (mín. 6)" required minLength={6} />
          <button className="btn primary" disabled={loading}>{loading ? 'Salvando…' : 'Cadastrar'}</button>
        </form>
      </section>

      <section className="card">
        <h3>Cadastrados</h3>
        {users.length === 0 ? (
          <p className="empty">Nenhum usuário cadastrado.</p>
        ) : (
          <table>
            <thead><tr><th>Nome</th><th>Email</th><th>Perfil</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>{u.name}</td>
                  <td>{u.email}</td>
                  <td>{u.role === 'ADMIN' ? 'Administrador' : 'Atendente'}</td>
                  <td>
                    <span className={`tag ${u.active ? 'green' : 'gray'}`}>{u.active ? 'Ativo' : 'Inativo'}</span>
                  </td>
                  <td>
                    <button className="btn small ghost" onClick={() => toggleActive(u)}>
                      {u.active ? 'Desativar' : 'Ativar'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  )
}
