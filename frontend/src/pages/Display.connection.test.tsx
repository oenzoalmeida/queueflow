import { act, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Display from './Display'

const statusListeners: ((s: string) => void)[] = []
const topicCallbacks: ((body: any) => void)[] = []

vi.mock('../ws', () => ({
  subscribe: vi.fn((_topic: string, cb: (body: any) => void) => {
    topicCallbacks.push(cb)
    return vi.fn()
  }),
  subscribeStatus: vi.fn((cb: (s: string) => void) => {
    statusListeners.push(cb)
    cb('disconnected') // immediate current value, like the real module
    return vi.fn()
  }),
}))

const mockGet = vi.fn()
vi.mock('../api/client', () => ({ api: { get: (...args: any[]) => mockGet(...args) } }))

const mockPlay = vi.fn()
// Stable object: the real hook memoizes its return; a fresh object per render would
// re-run Display's effect (dep [refresh, sound]) forever. Hoisted via factory scope.
vi.mock('../sound', () => {
  const soundValue = { enabled: true, enable: vi.fn(), play: (...a: any[]) => mockPlay(...a) }
  return { useCallSound: () => soundValue }
})

const emitStatus = (s: string) => act(() => statusListeners.forEach((l) => l(s)))
const emitMessage = (body: any) => act(() => topicCallbacks.forEach((cb) => cb(body)))

describe('Display connection handling', () => {
  beforeEach(() => {
    statusListeners.length = 0
    topicCallbacks.length = 0
    mockGet.mockReset().mockResolvedValue({ data: { highlight: null, lastCalls: [], waiting: 0 } })
    mockPlay.mockReset()
  })

  it('warns the data is stale when the socket drops and resyncs the snapshot on reconnect', async () => {
    render(<Display />)
    await screen.findByText(/Nenhuma chamada ainda/)
    expect(mockGet).toHaveBeenCalledTimes(1)

    emitStatus('connected') // first connect also resyncs
    await waitFor(() => expect(mockGet).toHaveBeenCalledTimes(2))
    expect(screen.queryByText(/Sem conexão/)).not.toBeInTheDocument()

    emitStatus('disconnected') // queda: estado visual muda sem nova mensagem
    expect(screen.getByText(/Sem conexão/)).toBeInTheDocument()

    emitStatus('connected') // retorno: snapshot recarregado
    await waitFor(() => expect(mockGet).toHaveBeenCalledTimes(3))
    expect(screen.queryByText(/Sem conexão/)).not.toBeInTheDocument()
  })

  it('plays the call sound once per ticket, not again on a recall of the same ticket', async () => {
    render(<Display />)
    await screen.findByText(/Nenhuma chamada ainda/)
    emitStatus('connected')

    const ticket = { id: 7, displayCode: 'A007', counterName: 'Guichê 2' }
    emitMessage({ type: 'TICKET_CALLED', payload: { ticket } })
    emitMessage({ type: 'TICKET_RECALLED', payload: { ticket } }) // mesma senha: sem novo som
    expect(mockPlay).toHaveBeenCalledTimes(1)

    emitMessage({ type: 'TICKET_CALLED', payload: { ticket: { id: 8, displayCode: 'A008', counterName: 'Guichê 2' } } })
    expect(mockPlay).toHaveBeenCalledTimes(2)
  })
})
