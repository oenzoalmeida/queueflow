import { describe, it, expect } from 'vitest'
import { STATUS_LABEL, type TicketDTO } from './types'

// Todo status da união TicketDTO['status'] precisa de rótulo em pt-BR: um status
// novo sem entrada aqui renderizaria cru na interface (Display, History...).
const ALL_STATUSES: TicketDTO['status'][] = ['WAITING', 'CALLED', 'IN_SERVICE', 'FINISHED', 'ABSENT', 'CANCELLED']

describe('STATUS_LABEL', () => {
  it('cobre todos os status de senha com rótulo preenchido', () => {
    for (const status of ALL_STATUSES) {
      expect(STATUS_LABEL[status], `status ${status} sem rótulo`).toBeTruthy()
    }
  })

  it('não tem rótulo órfão de status removido', () => {
    expect(Object.keys(STATUS_LABEL).sort()).toEqual([...ALL_STATUSES].sort())
  })
})
