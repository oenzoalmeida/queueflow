import { describe, it, expect } from 'vitest'
import { errMessage } from './client'

describe('errMessage', () => {
  it('concatena erros de campo separados por " · "', () => {
    const err = { response: { data: { fields: { email: 'E-mail inválido', password: 'Senha obrigatória' } } } }
    expect(errMessage(err)).toBe('E-mail inválido · Senha obrigatória')
  })

  it('usa a mensagem da API quando não há erros de campo', () => {
    expect(errMessage({ response: { data: { message: 'Credenciais inválidas' } } })).toBe('Credenciais inválidas')
  })

  it('cai na mensagem genérica sem resposta da API', () => {
    expect(errMessage(new Error('Network Error'))).toBe('Erro inesperado. Tente novamente.')
    expect(errMessage(undefined)).toBe('Erro inesperado. Tente novamente.')
  })
})
