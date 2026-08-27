import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

/**
 * Short "ding" for the public panel using WebAudio (no asset needed).
 * Browsers block audio before user interaction: caller must first call enable()
 * from a click handler. `enabled` starts false until the user activates it.
 */
export function useCallSound() {
  const [enabled, setEnabled] = useState(false)
  const ctxRef = useRef<AudioContext | null>(null)

  const enable = useCallback(() => {
    if (!ctxRef.current) ctxRef.current = new AudioContext()
    ctxRef.current.resume().catch(() => {})
    setEnabled(true)
  }, [])

  useEffect(() => {
    return () => {
      ctxRef.current?.close().catch(() => {})
    }
  }, [])

  const play = useCallback(() => {
    const ctx = ctxRef.current
    if (!enabled || !ctx || ctx.state !== 'running') return
    const t = ctx.currentTime
    ;[880, 1174.66].forEach((freq, i) => {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = freq
      gain.gain.setValueAtTime(0.0001, t + i * 0.18)
      gain.gain.exponentialRampToValueAtTime(0.35, t + i * 0.18 + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, t + i * 0.18 + 0.5)
      osc.connect(gain).connect(ctx.destination)
      osc.start(t + i * 0.18)
      osc.stop(t + i * 0.18 + 0.55)
    })
  }, [enabled])

  return useMemo(() => ({ enabled, enable, play }), [enabled, enable, play])
}
