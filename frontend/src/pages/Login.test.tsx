import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

const { getMock, postMock } = vi.hoisted(() => ({ getMock: vi.fn(), postMock: vi.fn() }))

vi.mock('../api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api/client')>()
  return {
    ...original,
    api: { get: getMock, post: postMock, interceptors: { response: { use: vi.fn() } } },
  }
})

import { AuthProvider } from '../auth'
import Login from './Login'

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Login />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('Login', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Sessão anônima: /auth/me falha, formulário deve aparecer.
    getMock.mockRejectedValue(new Error('unauthenticated'))
  })

  it('renderiza o formulário de acesso', async () => {
    renderLogin()
    expect(await screen.findByRole('heading', { name: 'QueueFlow' })).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeRequired()
    expect(screen.getByLabelText('Senha')).toBeRequired()
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled()
  })

  it('exibe a mensagem de erro da API quando o login é recusado', async () => {
    postMock.mockRejectedValue({ response: { data: { message: 'Credenciais inválidas' } } })
    renderLogin()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Email'), 'a@b.com')
    await user.type(screen.getByLabelText('Senha'), 'qualquer-coisa')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Credenciais inválidas')).toBeInTheDocument()
    expect(postMock).toHaveBeenCalledWith('/auth/login', { email: 'a@b.com', password: 'qualquer-coisa' })
  })
})
