import { defineConfig } from '@playwright/test'

/**
 * E2E em navegador real (Chromium). Sobe contra um backend local (perfil test,
 * PostgreSQL em :5433) e o build de produção servido por `vite preview` em :5173.
 * Ver .github/workflows/ci.yml (job e2e) e docs/e2e.md para o passo a passo local.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: 0,
  workers: 1, // o fluxo compartilha um único banco efêmero; serial evita interferência
  use: {
    baseURL: 'http://localhost:5173',
    viewport: { width: 1440, height: 900 },
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
})
