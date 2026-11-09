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
  test.setTimeout(300_000) // CI e mais lenta que o ambiente local
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

  // guichê próprio para a captura da tela do atendente (idempotente): a spec principal
  // deixa o Guichê 1 ocupado pela Ana E2E (claim persiste no banco do job)
  const counters = await request.get(`${API}/counters`)
  let guiche = (await counters.json()).find((c: { id: number; name: string }) => c.name === 'Guichê 2')
  if (!guiche) {
    const c = await request.post(`${API}/counters`, { data: { name: 'Guichê 2' } })
    expect(c.ok()).toBeTruthy()
    guiche = await c.json()
  }

  await page.goto('/login')
  await page.screenshot({ path: `${DIR}/login.png` })

  await page.getByLabel(/e-?mail/i).fill(ADMIN.email)
  await page.getByRole('textbox', { name: 'Senha' }).fill(ADMIN.password)
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
  const attCreds = { name: 'Bia E2E', email: 'bia-e2e@test.io', temp: 'SenhaTemp1234', final: 'SenhaBia12345' }
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
  // tolerância: se a atendente já tiver um guichê (rerodada local), a tela abre
  // direto no painel do guichê, sem o select
  const callBtn = att.getByRole('button', { name: 'CHAMAR PRÓXIMO' })
  const sel = att.getByLabel('Escolha seu guichê')
  await expect(sel.or(callBtn)).toBeVisible({ timeout: 30_000 })
  if (await sel.isVisible()) {
    await sel.selectOption(String(guiche.id))
    await att.getByRole('button', { name: 'Entrar no guichê' }).click()
  }
  await callBtn.click()
  await expect(att.locator('.code-xl')).not.toHaveText('—')
  await att.getByRole('button', { name: 'INICIAR ATENDIMENTO' }).click()
  await expect(att.getByText('EM ATENDIMENTO')).toBeVisible()
  await att.screenshot({ path: `${DIR}/atendimento.png` })

  // display agora mostra a senha chamada em destaque (atualização em tempo real)
  await expect(display.locator('.display-code')).not.toHaveText('—', { timeout: 10_000 })
  await display.screenshot({ path: `${DIR}/display.png` })
  await attCtx.close()
})
