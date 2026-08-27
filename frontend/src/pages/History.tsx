import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { STATUS_LABEL, type QueueDTO, type TicketDTO, type UserDTO } from '../types'

interface Row { ticket: TicketDTO; waitMinutes: number | null; serviceMinutes: number | null; totalMinutes: number | null }

const fmtTime = (iso: string | null) =>
  iso ? new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—'
const fmtMin = (v: number | null) => (v == null ? '—' : `${v} min`)

export default function History() {
  const [rows, setRows] = useState<Row[]>([])
  const [totalPages, setTotalPages] = useState(1)
  const [page, setPage] = useState(0)
  const [queues, setQueues] = useState<QueueDTO[]>([])
  const [attendants, setAttendants] = useState<UserDTO[]>([])
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10))
  const [queueId, setQueueId] = useState('')
  const [status, setStatus] = useState('')
  const [attendantId, setAttendantId] = useState('')

  useEffect(() => {
    Promise.all([api.get('/queues'), api.get('/users')]).then(([q, u]) => {
      setQueues(q.data); setAttendants(u.data)
    }).catch(() => {})
  }, [])

  async function load(p = page) {
    const params = new URLSearchParams({ date, page: String(p), size: '20' })
    if (queueId) params.set('queueId', queueId)
    if (status) params.set('status', status)
    if (attendantId) params.set('attendantId', attendantId)
    const { data } = await api.get(`/history?${params}`)
    setRows(data.content); setTotalPages(data.totalPages || 1); setPage(data.number ?? p)
  }

  useEffect(() => { load(0).catch(() => {}) }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    load(0).catch(() => {})
  }

  return (
    <>
      <h2>Histórico</h2>
      <section className="card">
        <form className="filter-form" onSubmit={submit}>
          <label>Data <input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
          <label>Fila
            <select value={queueId} onChange={(e) => setQueueId(e.target.value)}>
              <option value="">Todas</option>
              {queues.map((q) => <option key={q.id} value={q.id}>{q.name}</option>)}
            </select>
          </label>
          <label>Status
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">Todos</option>
              {Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label>Atendente
            <select value={attendantId} onChange={(e) => setAttendantId(e.target.value)}>
              <option value="">Todos</option>
              {attendants.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </label>
          <button className="btn primary">Filtrar</button>
        </form>
      </section>

      <section className="card">
        {rows.length === 0 ? (
          <p className="empty">Nenhuma senha encontrada para os filtros escolhidos.</p>
        ) : (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Senha</th><th>Fila</th><th>Tipo</th><th>Emissão</th><th>Chamada</th>
                    <th>Início</th><th>Fim</th><th>Atendente</th><th>Guichê</th><th>Status</th>
                    <th>Espera</th><th>Atend.</th><th>Total</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(({ ticket: t, waitMinutes, serviceMinutes, totalMinutes }) => (
                    <tr key={t.id}>
                      <td className="mono">{t.displayCode}</td>
                      <td>{t.queueName}</td>
                      <td>{t.priorityType === 'PRIORITY' ? 'Prioritária' : 'Normal'}</td>
                      <td>{fmtTime(t.createdAt)}</td>
                      <td>{fmtTime(t.calledAt)}</td>
                      <td>{fmtTime(t.serviceStartedAt)}</td>
                      <td>{fmtTime(t.finishedAt)}</td>
                      <td>{t.attendantName ?? '—'}</td>
                      <td>{t.counterName ?? '—'}</td>
                      <td>{STATUS_LABEL[t.status]}</td>
                      <td>{fmtMin(waitMinutes)}</td>
                      <td>{fmtMin(serviceMinutes)}</td>
                      <td>{fmtMin(totalMinutes)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="pager">
              <button className="btn small ghost" disabled={page <= 0} onClick={() => load(page - 1)}>← Anterior</button>
              <span>Página {page + 1} de {totalPages}</span>
              <button className="btn small ghost" disabled={page >= totalPages - 1} onClick={() => load(page + 1)}>Próxima →</button>
            </div>
          </>
        )}
      </section>
    </>
  )
}
