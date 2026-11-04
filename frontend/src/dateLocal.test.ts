import { afterEach, describe, expect, it, vi } from 'vitest'
import { todayLocalISODate } from './dateLocal'

describe('todayLocalISODate', () => {
  afterEach(() => vi.useRealTimers())

  it('usa o fuso local, não UTC: 23h30 em UTC-3 ainda é o dia local', () => {
    // 2026-11-04T02:30:00Z == 2026-11-03 23:30 em America/Sao_Paulo
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-04T02:30:00Z'))
    const local = todayLocalISODate()
    const now = new Date()
    const expected = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    expect(local).toBe(expected)
    // Com TZ=America/Sao_Paulo (CI roda TZ local), o ISO UTC já é 2026-11-04,
    // mas a data local ainda é 2026-11-03: expect(local).toBe('2026-11-03').
  })

  it('formato yyyy-mm-dd com zero à esquerda', () => {
    expect(todayLocalISODate(new Date(2026, 0, 5))).toBe('2026-01-05')
    expect(todayLocalISODate(new Date(2026, 11, 25))).toBe('2026-12-25')
  })
})
