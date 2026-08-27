import { useEffect, useState } from 'react'
import { api, errMessage } from '../api/client'
import type { QueueDTO } from '../types'

export default function Queues() {
  const [queues, setQueues] = useState<QueueDTO[]>([])
  const [name, setName] = useState('')
  const [prefix, setPrefix] = useState('')
  const [editing, setEditing] = useState<number | null>(null)
  const [editActive, setEditActive] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function load() {
    setQueues((await api.get('/queues')).data)
  }
  useEffect(() => { load().catch(() => setError('Falha ao carregar filas.')) }, [])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      if (editing) await api.put(`/queues/${editing}`, { name, prefix, active: editActive })
      else await api.post('/queues', { name, prefix })
      setName(''); setPrefix(''); setEditing(null)
      await load()
    } catch (err) {
      setError(errMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const startEdit = (q: QueueDTO) => {
    setEditing(q.id); setName(q.name); setPrefix(q.prefix); setEditActive(q.active); setError(null)
  }

  return (
    <>
      <h2>Filas</h2>
      <section className="card">
        <h3>{editing ? 'Editar fila' : 'Nova fila'}</h3>
        {error && <div className="alert error">{error}</div>}
        <form className="inline-form" onSubmit={submit}>
          <input
            value={name} onChange={(e) => setName(e.target.value)}
            placeholder="Ex.: Atendimento Geral" required maxLength={120}
          />
          <input
            className="prefix-input" value={prefix}
            onChange={(e) => setPrefix(e.target.value.toUpperCase().replace(/[^A-Z]/g, '').slice(0, 3))}
            placeholder="Prefixo (ex.: A)" required maxLength={3}
          />
          {editing && (
            <label className="checkbox">
              <input type="checkbox" checked={editActive} onChange={(e) => setEditActive(e.target.checked)} /> Ativa
            </label>
          )}
          <button className="btn primary" disabled={loading}>{loading ? 'Salvando…' : editing ? 'Salvar' : 'Criar fila'}</button>
          {editing && <button type="button" className="btn ghost" onClick={() => { setEditing(null); setName(''); setPrefix('') }}>Cancelar</button>}
        </form>
        <p className="hint">O prefixo compõe o código das senhas desta fila (ex.: A001).</p>
      </section>

      <section className="card">
        <h3>Minhas filas</h3>
        {queues.length === 0 ? (
          <p className="empty">Crie sua primeira fila para começar.</p>
        ) : (
          <table>
            <thead><tr><th>Nome</th><th>Prefixo</th><th>Ativa</th><th></th></tr></thead>
            <tbody>
              {queues.map((q) => (
                <tr key={q.id}>
                  <td>{q.name}</td>
                  <td className="mono">{q.prefix}</td>
                  <td><span className={`tag ${q.active ? 'green' : 'gray'}`}>{q.active ? 'Ativa' : 'Inativa'}</span></td>
                  <td><button className="btn small ghost" onClick={() => startEdit(q)}>Editar</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  )
}
