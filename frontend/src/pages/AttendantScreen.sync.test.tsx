import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AttendantScreen from './AttendantScreen'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  subscribe: vi.fn(),
}))
vi.mock('../api/client', () => ({
  api: { get: mocks.get, post: mocks.post },
  errMessage: () => 'Falha ao executar ação.',
}))
vi.mock('../auth', () => ({
  useAuth: () => ({ user: { name: 'Ana' }, logout: vi.fn() }),
}))
vi.mock('../ws', () => ({ subscribe: (...args: unknown[]) => mocks.subscribe(...args) }))

const current = {
  id: 7, displayCode: 'A007', queueName: 'Geral',
  status: 'CALLED', calledAt: '2026-11-04T10:00:00Z',
}

describe('AttendantScreen synchronization failures', () => {
  let refresh!: () => void
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.post.mockReset()
    mocks.subscribe.mockReset()
    mocks.subscribe.mockImplementation((_topic: string, callback: () => void) => {
      refresh = callback
      return vi.fn()
    })
    mocks.get.mockImplementation((url: string) => {
      if (url === '/counters') return Promise.resolve({ data: [{ id: 1, name: 'Guichê 1', active: true, currentAttendantName: 'Ana' }] })
      if (url.includes('/tickets/state')) return Promise.resolve({ data: { current, waiting: 3 } })
      return Promise.reject(new Error('unknown URL'))
    })
  })

  it('keeps the last ticket and disables actions on a failed refresh', async () => {
    render(<AttendantScreen />)
    expect(await screen.findByText('A007')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()

    mocks.get.mockImplementation((url: string) =>
      url.includes('/tickets/state') ? Promise.reject(new Error('offline')) : Promise.resolve({ data: [] }),
    )
    await act(async () => { refresh(); await Promise.resolve() })

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível atualizar')
    expect(screen.getByText('A007')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'RECHAMAR' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'INICIAR ATENDIMENTO' })).toBeDisabled()
    expect(mocks.post).not.toHaveBeenCalled()
  })

  it('recovers actions when a subsequent refresh succeeds', async () => {
    render(<AttendantScreen />)
    await screen.findByText('A007')
    mocks.get.mockRejectedValueOnce(new Error('offline'))
    await act(async () => { refresh(); await Promise.resolve() })
    await screen.findByRole('alert')

    mocks.get.mockImplementation((url: string) =>
      url.includes('/tickets/state') ? Promise.resolve({ data: { current, waiting: 2 } }) : Promise.resolve({ data: [] }),
    )
    await act(async () => { refresh(); await Promise.resolve() })
    await waitFor(() => expect(screen.getByRole('button', { name: 'RECHAMAR' })).not.toBeDisabled())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
  it('keeps an action failure visible even when resynchronization succeeds', async () => {
    mocks.post.mockRejectedValueOnce(new Error('server failure'))
    render(<AttendantScreen />)
    await screen.findByText('A007')

    fireEvent.click(screen.getByRole('button', { name: 'RECHAMAR' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Falha ao executar ação.')
    await waitFor(() => expect(mocks.get).toHaveBeenCalledWith('/tickets/state?counterId=1'))
    expect(screen.getByRole('alert')).toHaveTextContent('Falha ao executar ação.')
    expect(screen.getByRole('button', { name: 'RECHAMAR' })).not.toBeDisabled()
  })

})
