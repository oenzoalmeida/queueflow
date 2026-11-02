import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('sockjs-client', () => ({ default: class SockJS {} }))

let mockConfig: Record<string, any> | undefined
let mockClient: any

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    connected = false
    activate = vi.fn(() => {
      this.connected = true
    })
    subscribe = vi.fn().mockReturnValue({ unsubscribe: vi.fn() })
    constructor(config: Record<string, any>) {
      mockConfig = config
      mockClient = this
    }
  },
}))

async function loadWs() {
  vi.resetModules()
  return await import('./ws')
}

describe('ws connection status', () => {
  beforeEach(() => {
    mockConfig = undefined
    mockClient = undefined
  })

  it('tracks the real socket lifecycle: disconnected -> connecting -> connected -> disconnected', async () => {
    const ws = await loadWs()
    expect(ws.getWsStatus()).toBe('disconnected')

    const events: string[] = []
    const unsubStatus = ws.subscribeStatus((s) => events.push(s))
    expect(events).toEqual(['disconnected']) // immediate current value

    ws.subscribe('/topic/display', () => {})
    expect(events).toEqual(['disconnected', 'connecting'])

    mockConfig!.onConnect()
    expect(events).toEqual(['disconnected', 'connecting', 'connected'])

    // Drop without any message arriving: status must still flip.
    mockConfig!.onWebSocketClose()
    expect(events[events.length - 1]).toBe('disconnected')

    mockConfig!.onConnect()
    expect(events[events.length - 1]).toBe('connected')

    unsubStatus()
  })

  it('resubscribes topics on reconnect so no events are silently lost', async () => {
    const ws = await loadWs()
    ws.subscribe('/topic/display', () => {})

    mockConfig!.onConnect()
    expect(mockClient.subscribe).toHaveBeenCalledTimes(1)

    mockConfig!.onWebSocketClose()
    mockConfig!.onConnect() // reconnect: must subscribe again
    expect(mockClient.subscribe).toHaveBeenCalledTimes(2)
    expect(mockClient.subscribe.mock.calls[1][0]).toBe('/topic/display')
  })

  it('stops notifying after unsubscribe from status', async () => {
    const ws = await loadWs()
    const events: string[] = []
    const unsub = ws.subscribeStatus((s) => events.push(s))
    ws.subscribe('/topic/x', () => {})
    unsub()
    mockConfig!.onConnect()
    expect(events).toEqual(['disconnected', 'connecting'])
  })
})
