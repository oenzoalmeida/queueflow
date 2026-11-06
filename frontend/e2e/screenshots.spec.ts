import { test, expect, type APIRequestContext } from '@playwright/test'

/**
 * Gera as capturas reais usadas no README/LinkedIn, contra o mesmo ambiente
 * e2e local (backend :8080 perfil test, preview :5173). Roda DEPOIS de
 * queueflow.spec.ts? Não: é independente, usa seu próprio prefixo de fila (S).
 * Saída: ../docs/screenshots/*.png
 */

const API = process.env.E2E_API_URL ?? 'http://localhost:8080/api'
const ADMIN = { name: 'Admin E2E', email: 'admin-e2e@test.io', password: 'SenhaAdmin123' }
const DIR = '../docs/screenshots'

test('capturas das telas para documentação', async ({ page, request, browser }) => {
  test.setTimeout(180_000)
  // bootstrap idempotente: se o admin já existe (spec principal rodou antes), só faz login
  const reg = await request.post(`${API}/auth/register`, {
    data: { name: ADMIN.name, email: ADMIN.email, password: ADMIN.password },
  })
  if (!reg.ok()) {
    const login = await request.post(`${API}/auth/login`, { data: { email: ADMIN.email, password: ADMIN.password } })
    expect(login.ok()).toBeTruthy()
  }

  // garante dados visíveis: fila S + 2 senhas aguardando (idempotente p/ rerodadas locais)
  const list = await request.get(`${API}/queues`)
  const existing = (await list.json()).find((x: { name: string }) => x.name === 'Preferencial')
  let qid: number
  if (existing) {
    qid = existing.id
  } else {
    const queue = await request.post(`${API}/queues`, { data: { name: 'Preferencial', prefix: 'S' } })
    expect(queue.ok()).toBeTruthy()
    qid = (await queue.json()).id
    for (const priorityType of ['NORMAL', 'PRIORITY']) {
      const t = await request.post(`${API}/public/tickets`, { data: { queueId: qid, priorityType } })
      expect(t.ok()).toBeTruthy()
    }
  }

  // guichê para a captura da tela do atendente (idempotente)
  const counters = await request.get(`${API}/counters`)
  if (!(await counters.json()).some((c: { name: string }) => c.name === 'Guichê 1')) {
    const c = await request.post(`${API}/counters`, { data: { name: 'Guichê 1' } })
    expect(c.ok()).toBeTruthy()
  }

  await page.goto('/login')
  await page.screenshot({ path: `${DIR}/login.png` })

  await page.getByLabel(/e-?mail/i).fill(ADMIN.email)
  await page.getByLabel(/senha/i).fill(ADMIN.password)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page).toHaveTitle('Dashboard · QueueFlow')
  await page.getByText('Senhas hoje').waitFor()
  await page.screenshot({ path: `${DIR}/02-dashboard.png` })

  const totem = await browser.newPage()
  await totem.goto('/totem')
  await totem.getByRole('button', { name: /Atendimento Geral E2E|Preferencial/ }).first().waitFor()
  await totem.screenshot({ path: `${DIR}/03-totem.png` })
  await totem.getByRole('button', { name: /Preferencial/ }).click()
  await totem.getByRole('button', { name: 'Emitir minha senha' }).click()
  await expect(totem.locator('.totem-code')).toHaveText(/S\d{3}/)
  await totem.screenshot({ path: `${DIR}/04-totem-senha.png` })

  const display = await browser.newPage()
  await display.goto('/display')
  await display.getByRole('heading', { name: 'Últimas chamadas' }).waitFor()

  // atendente com senha em atendimento (também alimenta o display via WebSocket)
  const attCreds = { name: 'Ana E2E', email: 'ana-e2e@test.io', temp: 'SenhaTemp1234', final: 'SenhaAna12345' }
  const mk = await request.post(`${API}/users`, { data: { name: attCreds.name, email: attCreds.email, password: attCreds.temp } })
  const attCtx = await browser.newContext()
  if (mk.ok()) {
    const login = await attCtx.request.post(`${API}/auth/login`, { data: { email: attCreds.email, password: attCreds.temp } })
    expect(login.ok()).toBeTruthy()
    const changed = await attCtx.request.post(`${API}/auth/change-password`, {
      data: { currentPassword: attCreds.temp, newPassword: attCreds.final },
    })
    expect(changed.ok()).toBeTruthy()
  } else {
    const login = await attCtx.request.post(`${API}/auth/login`, { data: { email: attCreds.email, password: attCreds.final } })
    expect(login.ok()).toBeTruthy()
  }
  const att = await attCtx.newPage()
  await att.goto('/attendant')
  await att.getByLabel('Escolha seu guichê').selectOption({ label: 'Guichê 1' })
  await att.getByRole('button', { name: 'Entrar no guichê' }).click()
  await att.getByRole('button', { name: 'CHAMAR PRÓXIMO' }).click()
  await expect(att.locator('.code-xl')).not.toHaveText('—')
  await att.getByRole('button', { name: 'INICIAR ATENDIMENTO' }).click()
  await expect(att.getByText('EM ATENDIMENTO')).toBeVisible()
  await att.screenshot({ path: `${DIR}/atendimento.png` })

  // display agora mostra a senha chamada em destaque (atualização em tempo real)
  await expect(display.locator('.display-code')).not.toHaveText('—', { timeout: 10_000 })
  await display.screenshot({ path: `${DIR}/display.png` })
  await attCtx.close()
})
