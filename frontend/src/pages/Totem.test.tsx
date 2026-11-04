import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Totem from './Totem'

const apiMock = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../api/client', () => ({ api: apiMock }))

beforeEach(() => {
  apiMock.get.mockReset()
  apiMock.post.mockReset()
})

describe('public ticket totem', () => {
  it('distinguishes connection failure from a genuinely empty queue and lets visitors retry', async () => {
    apiMock.get.mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ data: [{ id: 1, name: 'Atendimento geral', prefix: 'A' }] })
    render(<Totem />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Não conseguimos carregar')
    expect(screen.queryByText('Nenhuma fila disponível no momento.')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    expect(await screen.findByRole('button', { name: /Atendimento geral/ })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows the ticket issue error without leaving the chosen queue', async () => {
    apiMock.get.mockResolvedValue({ data: [{ id: 1, name: 'Atendimento geral', prefix: 'A' }] })
    apiMock.post.mockRejectedValueOnce(new Error('service down'))
    render(<Totem />)
    fireEvent.click(await screen.findByRole('button', { name: /Atendimento geral/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Emitir minha senha' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível emitir a senha')
    expect(screen.getByRole('button', { name: 'Emitir minha senha' })).toBeEnabled()
  })

  it('blocks two simultaneous ticket submissions', async () => {
    let resolve!: (value: unknown) => void
    apiMock.get.mockResolvedValue({ data: [{ id: 1, name: 'Atendimento geral', prefix: 'A' }] })
    apiMock.post.mockImplementation(() => new Promise((r) => { resolve = r }))
    render(<Totem />)
    fireEvent.click(await screen.findByRole('button', { name: /Atendimento geral/ }))
    const issue = screen.getByRole('button', { name: 'Emitir minha senha' })
    fireEvent.click(issue)
    fireEvent.click(issue)
    expect(apiMock.post).toHaveBeenCalledTimes(1)
    resolve({ data: { displayCode: 'A001', peopleAhead: 0, estimatedWaitMinutes: 2, queueName: 'Atendimento geral', createdAt: '2026-11-04T15:00:00Z' } })
    expect(await screen.findByText('A001')).toBeInTheDocument()
  })
})
