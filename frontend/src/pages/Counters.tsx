import { useEffect, useState } from 'react'
import { api, errMessage } from '../api/client'
import type { CounterDTO } from '../types'

export default function Counters() {
  const [counters, setCounters] = useState<CounterDTO[]>([])
  const [name, setName] = useState('')
  const [editing, setEditing] = useState<number | null>(null)
  const [editActive, setEditActive] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function load() {
    setCounters((await api.get('/counters')).data)
  }
  useEffect(() => { load().catch(() => setError('Falha ao carregar guichês.')) }, [])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      if (editing) await api.put(`/counters/${editing}`, { name, active: editActive })
      else await api.post('/counters', { name })
      setName(''); setEditing(null)
      await load()
    } catch (err) {
      setError(errMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <h2>Guichês</h2>
      <section className="card">
        <h3>{editing ? 'Editar guichê' : 'Novo guichê'}</h3>
        {error && <div className="alert error">{error}</div>}
        <form className="inline-form" onSubmit={submit}>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Ex.: Guichê 01 ou Sala 03" required maxLength={80} />
          {editing && (
            <label className="checkbox">
              <input type="checkbox" checked={editActive} onChange={(e) => setEditActive(e.target.checked)} /> Ativo
            </label>
          )}
          <button className="btn primary" disabled={loading}>{loading ? 'Salvando…' : editing ? 'Salvar' : 'Criar guichê'}</button>
          {editing && <button type="button" className="btn ghost" onClick={() => { setEditing(null); setName('') }}>Cancelar</button>}
        </form>
      </section>

      <section className="card">
        <h3>Cadastrados</h3>
        {counters.length === 0 ? (
          <p className="empty">Nenhum guichê cadastrado.</p>
        ) : (
          <table>
            <thead><tr><th>Nome</th><th>Status</th><th>Ocupação</th><th></th></tr></thead>
            <tbody>
              {counters.map((c) => (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td><span className={`tag ${c.active ? 'green' : 'gray'}`}>{c.active ? 'Ativo' : 'Inativo'}</span></td>
                  <td>{c.currentAttendantName ?? 'Livre'}</td>
                  <td>
                    <button
                      className="btn small ghost"
                      onClick={() => { setEditing(c.id); setName(c.name); setEditActive(c.active); setError(null) }}
                    >
                      Editar
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
