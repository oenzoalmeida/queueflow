import { test, expect, type Browser, type APIRequestContext } from '@playwright/test'

/**
 * Fluxo crítico ponta a ponta em navegador real:
 * totem emite senha -> atendente chama no guichê -> display público mostra a
 * chamada em tempo real via WebSocket (sem reload).
 *
 * Pré-condição: banco PostgreSQL vazio (o bootstrap cria o primeiro admin).
 * As capturas em docs/screenshots são geradas por e2e/screenshots.spec.ts.
 */

const API = process.env.E2E_API_URL ?? 'http://localhost:8080/api'
const ADMIN = { name: 'Admin E2E', email: 'admin-e2e@test.io', password: 'SenhaAdmin123' }
const ATT = { name: 'Ana E2E', email: 'ana-e2e@test.io', temp: 'SenhaTemp1234', final: 'SenhaAna12345' }

async function bootstrapBackend(req: APIRequestContext) {
  const reg = await req.post(`${API}/auth/register`, {
    data: { name: ADMIN.name, email: ADMIN.email, password: ADMIN.password },
  })
  expect(reg.ok(), 'bootstrap do admin (banco deve estar vazio)').toBeTruthy()

  const queue = await req.post(`${API}/queues`, { data: { name: 'Atendimento Geral E2E', prefix: 'G' } })
  expect(queue.ok()).toBeTruthy()

  const counter = await req.post(`${API}/counters`, { data: { name: 'Guichê 1' } })
  expect(counter.ok()).toBeTruthy()

  const att = await req.post(`${API}/users`, { data: { name: ATT.name, email: ATT.email, password: ATT.temp } })
  expect(att.ok()).toBeTruthy()
  expect((await att.json()).mustChangePassword).toBe(true)
}

async function provisionAttendant(req: APIRequestContext) {
  const login = await req.post(`${API}/auth/login`, { data: { email: ATT.email, password: ATT.temp } })
  expect(login.ok()).toBeTruthy()
  expect((await login.json()).mustChangePassword).toBe(true)
  const changed = await req.post(`${API}/auth/change-password`, {
    data: { currentPassword: ATT.temp, newPassword: ATT.final },
  })
  expect(changed.ok()).toBeTruthy()
}

test('totem emite, atendente chama e display atualiza em tempo real', async ({ page, request, browser }) => {
  await bootstrapBackend(request)

  // login admin pela interface: título da aba e dashboard com indicadores reais
  await page.goto('/login')
  await expect(page).toHaveTitle('Entrar · QueueFlow')
  await page.getByLabel(/e-?mail/i).fill(ADMIN.email)
  await page.getByRole('textbox', { name: 'Senha' }).fill(ADMIN.password)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page).toHaveTitle('Dashboard · QueueFlow')
  await expect(page.getByText('Senhas hoje')).toBeVisible()

  // totem (público, sem login): emissão com prevenção de duplo envio
  const totem = await browser.newPage()
  await totem.goto('/totem')
  await expect(totem).toHaveTitle('Totem de senhas · QueueFlow')
  await totem.getByRole('button', { name: /Atendimento Geral E2E/ }).click()
  const issue = totem.getByRole('button', { name: 'Emitir minha senha' })
  await issue.click()
  await expect(totem.getByText('G001')).toBeVisible()
  await expect(totem.getByText(/pessoas? à sua frente/)).toBeVisible()

  // display público aberto ANTES da chamada: precisa atualizar sozinho (WebSocket)
  const display = await browser.newPage()
  await display.goto('/display')
  await expect(display).toHaveTitle('Painel de chamadas · QueueFlow')
  await expect(display.getByText('Aguardando chamadas')).toBeVisible()

  // atendente em contexto próprio (sessão separada da do admin); o provisionamento
  // pela API já deixa a sessão definitiva no cookie, então a tela abre logada
  const attCtx = await browser.newContext()
  await provisionAttendant(attCtx.request)
  const att = await attCtx.newPage()
  await att.goto('/attendant')
  await expect(att).toHaveTitle('Atendimento · QueueFlow')
  await att.getByLabel('Escolha seu guichê').selectOption({ label: 'Guichê 1' })
  await att.getByRole('button', { name: 'Entrar no guichê' }).click()
  await att.getByRole('button', { name: 'CHAMAR PRÓXIMO' }).click()
  await expect(att.getByText('G001')).toBeVisible()
  await att.getByRole('button', { name: 'INICIAR ATENDIMENTO' }).click()
  await expect(att.getByText('EM ATENDIMENTO')).toBeVisible()

  // o display mostra a senha chamada e o guichê sem recarregar a página
  await expect(display.getByText('G001')).toBeVisible({ timeout: 10_000 })
  await expect(display.getByText('Guichê 1')).toBeVisible()

  // finalizar atendimento mantém consistência no atendente
  await att.getByRole('button', { name: 'FINALIZAR' }).click()
  await expect(att.getByText('Nenhuma senha ativa neste guichê.')).toBeVisible()

  await attCtx.close()
})
