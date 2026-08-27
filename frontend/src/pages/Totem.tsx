import { useEffect, useState } from 'react'
import axios from 'axios'
import { ArrowLeft, Printer } from 'lucide-react'

interface PubQueue { id: number; name: string; prefix: string }
interface Issued {
  displayCode: string
  peopleAhead: number
  estimatedWaitMinutes: number | null
  queueName: string
  createdAt: string
}

const pub = axios.create({ baseURL: '/api/public' })

export default function Totem() {
  const [queues, setQueues] = useState<PubQueue[]>([])
  const [queue, setQueue] = useState<PubQueue | null>(null)
  const [priority, setPriority] = useState(false)
  const [issued, setIssued] = useState<Issued | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = () => pub.get('/queues').then((r) => setQueues(r.data)).catch(() => setError('Falha ao carregar filas.'))
  useEffect(() => { load() }, [])

  const confirm = async () => {
    if (!queue) return
    setLoading(true); setError(null)
    try {
      const { data } = await pub.post('/tickets', {
        queueId: queue.id,
        priorityType: priority ? 'PRIORITY' : 'NORMAL',
      })
      setIssued(data)
    } catch {
      setError('Não foi possível emitir a senha. Tente novamente.')
    } finally {
      setLoading(false)
    }
  }

  const reset = () => { setIssued(null); setQueue(null); setPriority(false); load() }

  return (
    <div className="totem-page">
      {issued ? (
        <div className="totem-result">
          <small className="muted">{issued.queueName} · emitida às {new Date(issued.createdAt).toLocaleTimeString('pt-BR')}</small>
          <h1 className="totem-code">{issued.displayCode}</h1>
          <p className="totem-ahead">{issued.peopleAhead} {issued.peopleAhead === 1 ? 'pessoa à sua frente' : 'pessoas à sua frente'}</p>
          <p className="totem-eta">
            {issued.estimatedWaitMinutes != null ? `Tempo estimado: ~${issued.estimatedWaitMinutes} minutos` : 'Estimativa ainda indisponível'}
          </p>
          <button className="btn primary huge" onClick={reset}>Emitir outra senha</button>
        </div>
      ) : !queue ? (
        <>
          <h1 className="totem-title">Como podemos ajudar?</h1>
          {error && <div className="alert error">{error}</div>}
          {queues.length === 0 ? (
            <p className="empty">Nenhuma fila disponível no momento.</p>
          ) : (
            <div className="totem-grid">
              {queues.map((q) => (
                <button key={q.id} className="totem-card" onClick={() => setQueue(q)}>
                  <span className="totem-prefix">{q.prefix}</span>
                  <span className="totem-name">{q.name}</span>
                </button>
              ))}
            </div>
          )}
        </>
      ) : (
        <>
          <h1 className="totem-title">{queue.name}</h1>
          <p className="muted center-block">Escolha o tipo de atendimento</p>
          <div className="totem-grid two">
            <button className={`totem-card choice ${!priority ? 'selected' : ''}`} onClick={() => setPriority(false)}>
              <span className="totem-name">Atendimento normal</span>
              <span className="totem-prefix">{queue.prefix}</span>
            </button>
            <button className={`totem-card choice priority ${priority ? 'selected' : ''}`} onClick={() => setPriority(true)}>
              <span className="totem-name">Atendimento prioritário</span>
              <span className="totem-prefix">P</span>
            </button>
          </div>
          <div className="totem-nav">
            <button className="btn ghost big-back" onClick={() => setQueue(null)}>
              <ArrowLeft size={20} /> Voltar
            </button>
            <button className="btn primary big-btn" onClick={confirm} disabled={loading}>
              {loading ? 'Emitindo…' : 'Confirmar'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}
