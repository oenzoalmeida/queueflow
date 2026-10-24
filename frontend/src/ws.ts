import SockJS from 'sockjs-client'
import { Client, type Message } from '@stomp/stompjs'

let client: Client | null = null
const pending: Set<(c: Client) => void> = new Set()

// Derived from VITE_API_URL by default (same backend, no separate config needed).
// VITE_WS_URL can override this for setups where the WS endpoint lives elsewhere.
const wsBase = import.meta.env.VITE_WS_URL ?? import.meta.env.VITE_API_URL ?? ''

function getClient(): Client {
  if (!client) {
    client = new Client({
      // SockJS picks ws:// vs wss:// from this URL's own scheme (http/https), so an
      // https:// VITE_API_URL in production automatically upgrades to a secure socket.
      webSocketFactory: () => new SockJS(`${wsBase}/ws`),
      reconnectDelay: 3000,
      onStompError: console.warn,
      onConnect: () => {
        // Resubscribe everything on (re)connect; each subscriber unsubscribes itself.
        for (const attach of pending) attach(client!)
      },
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
  else c.activate()
  return () => {
    pending.delete(attach)
    try {
      sub?.unsubscribe()
    } catch {
      /* noop */
    }
  }
}
