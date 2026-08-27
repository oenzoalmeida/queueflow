import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { subscribe } from '../ws'
import type { TicketDTO } from '../types'

interface DashboardData {
  issuedToday: number
  finishedToday: number
  waiting: number
  absentToday: number
  avgWaitMinutes: number | null
  avgServiceMinutes: number | null
  activeCounters: number
  byHour: { hour: number; count: number }[]
  byQueue: { queueName: string; issued: number }[]
  lastTickets: { displayCode: string; queueName: string; priorityType: string; status: string; attendantName: string | null; counterName: string | null }[]
}

const nf = (v: number | null, suffix = '') => (v == null ? '—' : `${v}${suffix}`)

export default function Dashboard() {
  const [data, setData] = useState<DashboardData | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function load() {
    try {
      setData((await api.get('/dashboard/today')).data)
    } catch (e) {
      setError('Falha ao carregar o dashboard.')
    }
  }

  useEffect(() => {
    load()
    const unsub = subscribe('/topic/display', () => void load())
    return unsub
  }, [])

  if (error) return <div className="alert error">{error}</div>
  if (!data) return <div className="loading">Carregando…</div>

  const maxHour = Math.max(1, ...data.byHour.map((h) => h.count))

  return (
    <>
      <h2>Dashboard</h2>
      <div className="stat-grid">
        <StatCard label="Senhas hoje" value={data.issuedToday} />
        <StatCard label="Atendimentos concluídos" value={data.finishedToday} />
        <StatCard label="Aguardando" value={data.waiting} />
        <StatCard label="Ausentes" value={data.absentToday} />
        <StatCard label="Tempo médio de espera" value={nf(data.avgWaitMinutes, ' min')} />
        <StatCard label="Tempo médio de atendimento" value={nf(data.avgServiceMinutes, ' min')} />
        <StatCard label="Guichês ativos" value={data.activeCounters} />
      </div>

      <div className="two-col">
        <section className="card">
          <h3>Atendimentos por hora</h3>
          {data.byHour.length === 0 ? (
            <p className="empty">Nenhum atendimento concluído hoje.</p>
          ) : (
            <div className="bars">
              {data.byHour.map(({ hour, count }) => (
                <div key={hour} className="bar-item" title={`${hour}:00 — ${count}`}>
                  <span>{count}</span>
                  <div className="bar" style={{ height: `${(count / maxHour) * 100}%` }} />
                  <small>{String(hour).padStart(2, '0')}h</small>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="card">
          <h3>Filas com maior volume</h3>
          {data.byQueue.length === 0 ? (
            <p className="empty">Nenhuma senha emitida hoje.</p>
          ) : (
            <ul className="mini-list">
              {data.byQueue.map((q) => (
                <li key={q.queueName}>
                  <span>{q.queueName}</span>
                  <strong>{q.issued}</strong>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="card">
        <h3>Últimas senhas</h3>
        {data.lastTickets.length === 0 ? (
          <p className="empty">Nenhuma senha emitida ainda.</p>
        ) : (
          <table>
            <thead>
              <tr><th>Senha</th><th>Fila</th><th>Tipo</th><th>Status</th><th>Atendente</th><th>Guichê</th></tr>
            </thead>
            <tbody>
              {data.lastTickets.map((t) => (
                <tr key={t.displayCode}>
                  <td className="mono">{t.displayCode}</td>
                  <td>{t.queueName}</td>
                  <td>{t.priorityType === 'PRIORITY' ? 'Prioritária' : 'Normal'}</td>
                  <td>{statusLabel(t.status)}</td>
                  <td>{t.attendantName ?? '—'}</td>
                  <td>{t.counterName ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  )
}

function statusLabel(s: string) {
  return ({
    WAITING: 'Aguardando', CALLED: 'Chamada', IN_SERVICE: 'Em atendimento',
    FINISHED: 'Concluída', ABSENT: 'Ausente', CANCELLED: 'Cancelada',
  } as Record<string, string>)[s] ?? s
}

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="card stat">
      <span className="stat-value">{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  )
}
