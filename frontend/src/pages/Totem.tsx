import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, Printer } from 'lucide-react'
import { api } from '../api/client'
import { usePageTitle } from '../usePageTitle'
import { TotemSkeleton } from '../components/Skeleton'

interface PubQueue { id: number; name: string; prefix: string }
interface Issued {
  displayCode: string
  peopleAhead: number
  estimatedWaitMinutes: number | null
  queueName: string
  createdAt: string
}

export default function Totem() {
  usePageTitle('Totem de senhas')
  const [queues, setQueues] = useState<PubQueue[] | null>(null)
  const [queue, setQueue] = useState<PubQueue | null>(null)
  const [priority, setPriority] = useState(false)
  const [issued, setIssued] = useState<Issued | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const requestInFlight = useRef(false)

  const load = () => {
    setError(null)
    setQueues(null)
    return api.get('/public/queues')
      .then((r) => setQueues(r.data))
      .catch(() => setError('Não conseguimos carregar as opções de atendimento. Confira a conexão e tente novamente.'))
  }
  useEffect(() => { load() }, [])

  const confirm = async () => {
    if (!queue || requestInFlight.current) return
    requestInFlight.current = true
    setLoading(true); setError(null)
    try {
      const { data } = await api.post('/public/tickets', {
        queueId: queue.id,
        priorityType: priority ? 'PRIORITY' : 'NORMAL',
      })
      setIssued(data)
    } catch {
      setError('Não foi possível emitir a senha. Tente novamente.')
    } finally {
      requestInFlight.current = false
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
          {error && <div role="alert" className="alert error">{error} <button type="button" className="btn ghost" onClick={() => void load()}>Tentar novamente</button></div>}
          {!queues ? (
            error ? null : <TotemSkeleton />
          ) : queues.length === 0 ? (
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
          <p className="muted center-block">Escolha o tipo de atendimento e toque em “Confirmar” para receber sua senha.</p>
          {error && <div role="alert" className="alert error">{error}</div>}
          <div className="totem-grid two">
            <button className={`totem-card choice ${!priority ? 'selected' : ''}`} aria-pressed={!priority} onClick={() => setPriority(false)}>
              <span className="totem-name">Atendimento normal</span>
              <span className="totem-prefix">{queue.prefix}</span>
            </button>
            <button className={`totem-card choice priority ${priority ? 'selected' : ''}`} aria-pressed={priority} onClick={() => setPriority(true)}>
              <span className="totem-name">Atendimento prioritário</span>
              <span className="totem-prefix">P</span>
            </button>
          </div>
          <div className="totem-nav">
            <button className="btn ghost big-back" onClick={() => setQueue(null)}>
              <ArrowLeft size={20} /> Voltar
            </button>
            <button className="btn primary big-btn" onClick={confirm} disabled={loading}>
              {loading ? 'Emitindo…' : 'Emitir minha senha'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}
