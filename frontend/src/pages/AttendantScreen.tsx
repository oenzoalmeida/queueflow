import { useCallback, useEffect, useState } from 'react'
import { api, errMessage } from '../api/client'
import { useAuth } from '../auth'
import { subscribe } from '../ws'
import type { CounterDTO, TicketDTO } from '../types'

interface StateResp { current: TicketDTO | null; waiting: number }

const fmtTime = (iso: string | null) =>
  iso ? new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : null

export default function AttendantScreen() {
  const { user, logout } = useAuth()
  const [counters, setCounters] = useState<CounterDTO[]>([])
  const [counterId, setCounterId] = useState<number | null>(null)
  const [claimedId, setClaimedId] = useState<number | null>(null)
  const [state, setState] = useState<StateResp | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadCounters = useCallback(async () => {
    const list: CounterDTO[] = (await api.get('/counters')).data
    setCounters(list)
    return list
  }, [])

  // restore previously claimed counter
  useEffect(() => {
    loadCounters().then((list) => {
      const mine = list.find((c) => c.currentAttendantName === user?.name)
      if (mine) { setCounterId(mine.id); setClaimedId(mine.id) }
    }).catch(() => setError('Falha ao carregar guichês.'))
  }, [])

  const loadState = useCallback(async (cid: number) => {
    try {
      setState((await api.get(`/tickets/state?counterId=${cid}`)).data)
    } catch (e) {
      setState({ current: null, waiting: 0 })
    }
  }, [])

  useEffect(() => {
    if (claimedId) loadState(claimedId)
  }, [claimedId, loadState])

  useEffect(() => {
    if (!claimedId) return
    const un1 = subscribe('/topic/display', () => void loadState(claimedId))
    return () => { un1(); }
  }, [claimedId, loadState])

  const claim = async () => {
    if (!counterId) return
    setBusy(true); setError(null)
    try {
      await api.post(`/counters/${counterId}/claim`)
      setClaimedId(counterId)
      await loadState(counterId)
      await loadCounters()
    } catch (e) {
      setError(errMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const releaseCounter = async () => {
    if (!claimedId) return
    setBusy(true); setError(null)
    try {
      await api.post('/counters/release')
      setCounterId(null); setClaimedId(null); setState(null)
    } catch (e) {
      setError(errMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const act = async (action: 'call-next' | 'recall' | 'start' | 'finish' | 'absent') => {
    if (!claimedId) return
    setBusy(true); setError(null)
    try {
      await api.post(`/tickets/${action}`, { counterId: claimedId })
      await loadState(claimedId)
    } catch (e) {
      setError(errMessage(e))
      await loadState(claimedId) // sync local state even on failure
    } finally {
      setBusy(false)
    }
  }

  const selectedCounter = counters.find((c) => c.id === (claimedId ?? counterId))
  const dropdownCounter = counters.find((c) => c.id === counterId)
  const occupiedByOther =
    dropdownCounter?.currentAttendantName != null && dropdownCounter.currentAttendantName !== user?.name

  return (
    <div className="attendant-page">
      <header className="attendant-top">
        <strong>QueueFlow · Atendimento</strong>
        <div className="spacer" />
        <span>{user?.name}</span>
        <button className="btn small ghost" onClick={logout}>Sair</button>
      </header>

      {!claimedId ? (
        <section className="card narrow center">
          <h3>Selecione seu guichê</h3>
          <select value={counterId ?? ''} onChange={(e) => setCounterId(Number(e.target.value))} className="big-select">
            <option value="" disabled>Escolher…</option>
            {counters.filter((c) => c.active).map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}{c.currentAttendantName ? ` — ocupado por ${c.currentAttendantName}` : ''}
              </option>
            ))}
          </select>
          <button className="btn primary big-btn" onClick={claim} disabled={!counterId || busy || occupiedByOther}>
            Entrar no guichê
          </button>
          {occupiedByOther && <p className="empty">Guichê ocupado por outro atendente.</p>}
        </section>
      ) : (
        <>
          <div className="workspace-head">
            <span className="tag green">Guichê: {selectedCounter?.name}</span>
            <span className="muted">Aguardando: <strong>{state?.waiting ?? '…'}</strong></span>
            <button className="btn small ghost" onClick={releaseCounter} disabled={busy}>Trocar guichê</button>
          </div>

          {error && <div className="alert error">{error}</div>}

          <section className="current-ticket card center">
            {state?.current ? (
              <>
                <small className="muted">
                  Senha atual · {state.current.queueName}
                  {state.current.calledAt ? ` · chamada às ${fmtTime(state.current.calledAt)}` : ''}
                </small>
                <div className="code-xl">{state.current.displayCode}</div>
                <span className={`tag ${state.current.status === 'IN_SERVICE' ? 'blue' : 'amber'}`}>
                  {state.current.status === 'IN_SERVICE' ? 'EM ATENDIMENTO' : 'CHAMADA'}
                </span>
              </>
            ) : (
              <>
                <div className="code-xl muted">—</div>
                <p className="empty">Nenhuma senha ativa neste guichê.</p>
              </>
            )}
          </section>

          <div className="actions-row">
            {state?.current == null && (
              <button className="btn primary huge" onClick={() => act('call-next')} disabled={busy}>
                CHAMAR PRÓXIMO
              </button>
            )}
            {state?.current?.status === 'CALLED' && (
              <>
                <button className="btn primary huge" onClick={() => act('start')} disabled={busy}>INICIAR ATENDIMENTO</button>
                <button className="btn ghost huge" onClick={() => act('recall')} disabled={busy}>RECHAMAR</button>
                <button className="btn danger huge" onClick={() => act('absent')} disabled={busy}>AUSENTE</button>
              </>
            )}
            {state?.current?.status === 'IN_SERVICE' && (
              <button className="btn primary huge" onClick={() => act('finish')} disabled={busy}>FINALIZAR</button>
            )}
          </div>
        </>
      )}
    </div>
  )
}
