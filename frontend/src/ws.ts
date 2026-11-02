import SockJS from 'sockjs-client'
import { Client, type Message } from '@stomp/stompjs'

let client: Client | null = null
const pending: Set<(c: Client) => void> = new Set()

// Derived from VITE_API_URL by default (same backend, no separate config needed).
// VITE_WS_URL can override this for setups where the WS endpoint lives elsewhere.
const wsBase = import.meta.env.VITE_WS_URL ?? import.meta.env.VITE_API_URL ?? ''

/** Connection status of the shared STOMP client, as observed by its own callbacks. */
export type WsStatus = 'connecting' | 'connected' | 'disconnected'

let status: WsStatus = 'disconnected'
const statusListeners = new Set<(s: WsStatus) => void>()

function setStatus(next: WsStatus) {
  if (status === next) return
  status = next
  for (const listener of statusListeners) listener(next)
}

/** Current connection status (no subscription needed). */
export function getWsStatus(): WsStatus {
  return status
}

/**
 * Subscribe to connection status changes; the listener fires immediately with the
 * current status and then on every transition. Returns an unsubscribe fn.
 */
export function subscribeStatus(cb: (s: WsStatus) => void): () => void {
  statusListeners.add(cb)
  cb(status)
  return () => {
    statusListeners.delete(cb)
  }
}

function getClient(): Client {
  if (!client) {
    client = new Client({
      // SockJS picks ws:// vs wss:// from this URL's own scheme (http/https), so an
      // https:// VITE_API_URL in production automatically upgrades to a secure socket.
      webSocketFactory: () => new SockJS(`${wsBase}/ws`),
      reconnectDelay: 3000,
      onStompError: console.warn,
      onConnect: () => {
        // Real status source: the STOMP handshake, not incoming messages. A silent
        // connection (no tickets) still counts as connected, and a dropped socket
        // flips the status even when no message ever arrives to contradict it.
        setStatus('connected')
        // Resubscribe everything on (re)connect; each subscriber unsubscribes itself.
        for (const attach of pending) attach(client!)
      },
      // SockJS/STOMP keep retrying on their own (reconnectDelay); this only reflects
      // the drop so screens can show the data may be stale until the next onConnect.
      onWebSocketClose: () => setStatus('disconnected'),
      onDisconnect: () => setStatus('disconnected'),
    })
  }
  return client
}

/** Subscribe to a topic; callback receives the parsed JSON body. Returns unsubscribe fn. */
export function subscribe(topic: string, cb: (body: any) => void): () => void {
  const c = getClient()
  let sub: { unsubscribe: () => void } | null = null
  const attach = (conn: Client) => {
    sub?.unsubscribe()
    sub = conn.subscribe(topic, (m: Message) => {
      try {
        cb(JSON.parse(m.body))
      } catch {
        /* ignore malformed */
      }
    })
  }
  pending.add(attach)
  if (c.connected) attach(c)
  else {
    setStatus('connecting')
    c.activate()
  }
  return () => {
    pending.delete(attach)
    try {
      sub?.unsubscribe()
    } catch {
      /* noop */
    }
  }
}
