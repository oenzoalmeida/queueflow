import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import { subscribe } from '../ws'
import { useCallSound } from '../sound'
import type { TicketDTO } from '../types'

interface DisplayState {
  highlight: TicketDTO | null
  lastCalls: TicketDTO[]
  waiting: number
}

async function fetchState(): Promise<DisplayState> {
  return (await api.get<DisplayState>('/public/display')).data
}

export default function Display() {
  const [state, setState] = useState<DisplayState>({ highlight: null, lastCalls: [], waiting: 0 })
  const [connected, setConnected] = useState(false)
  const lastCalledId = useRef<number | null>(null)
  const sound = useCallSound()

  const refresh = useCallback(async () => {
    try {
      setState(await fetchState())
    } catch {
      /* keeps last known state on TV */
    }
  }, [])

  useEffect(() => {
    refresh()
    const unsub = subscribe('/topic/display', (msg: { type: string; payload?: { ticket?: TicketDTO } }) => {
      setConnected(true)
      if (['TICKET_CALLED', 'TICKET_RECALLED'].includes(msg.type) && msg.payload?.ticket) {
        const t = msg.payload.ticket
        if (t.id !== lastCalledId.current) {
          lastCalledId.current = t.id
          sound.play()
        }
        setState((prev) => ({
          highlight: t,
          lastCalls: [t, prev.highlight, ...prev.lastCalls]
            .filter((x): x is TicketDTO => !!x && x.id !== t.id || x?.id === t.id)
            .filter((x, i, arr) => arr.findIndex((y) => y?.id === x?.id) === i)
            .slice(0, 6),
          waiting: prev.waiting,
        }))
      }
      void refresh()
    })
    return unsub
  }, [refresh, sound])

  return (
    <div className="display-page">
      <header className="display-top">
        <span className="brand-mini">QueueFlow</span>
        {!sound.enabled ? (
          <button className="sound-btn" onClick={sound.enable} title="Ativar som de chamada">
            🔇 Ativar som
          </button>
        ) : (
          <span className={`ws-dot ${connected ? 'on' : ''}`} title={connected ? 'Tempo real ativo' : 'Conectando…'} />
        )}
      </header>

      <section className="display-highlight">
        {state.highlight ? (
          <>
            <div className="display-code">{state.highlight.displayCode}</div>
            <div className="display-counter">{state.highlight.counterName ?? '—'}</div>
          </>
        ) : (
          <div className="display-idle">
            <div className="display-code muted">—</div>
            <p>Aguardando chamadas · {state.waiting} na fila</p>
          </div>
        )}
      </section>

      <section className="display-history">
        <h3>Últimas chamadas</h3>
        {state.lastCalls.length === 0 ? (
          <p className="empty">Nenhuma chamada ainda.</p>
        ) : (
          <ul>
            {state.lastCalls.slice(0, 5).map((t) => (
              <li key={t.id}>
                <span className="mono">{t.displayCode}</span>
                <span className="display-hist-counter">{t.counterName ?? '—'}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
