# Testes E2E em navegador real

O diretório `frontend/e2e` contém specs [Playwright](https://playwright.dev) que
sobem o fluxo crítico inteiro em Chromium de verdade: emissão no totem, chamada
pelo atendente e atualização do display público via WebSocket (sem reload).

## Rodando localmente

1. PostgreSQL em `localhost:5433` com banco **vazio** `queueflow_test`
   (usuário/senha `queueflow` — mesmo do perfil `test`; o bootstrap do primeiro
   admin exige banco sem usuários):
   ```bash
   dropdb -p 5433 -U queueflow queueflow_test 2>/dev/null
   createdb -p 5433 -U queueflow queueflow_test
   ```
2. Backend no perfil `test`:
   ```bash
   cd backend && ./mvnw -DskipTests package
   SPRING_PROFILES_ACTIVE=test java -jar target/*.jar
   ```
3. Frontend (build de produção servido em :5173, porta liberada no CORS do perfil test):
   ```bash
   cd frontend && npm ci
   VITE_API_URL=http://localhost:8080 VITE_WS_URL=http://localhost:8080 npm run build
   npx vite preview --port 5173 --strictPort &
   ```
4. Specs:
   ```bash
   npx playwright install chromium   # primeira vez
   npm run test:e2e                  # fluxo crítico + capturas para docs/screenshots
   ```

Na CI o job `e2e` executa os mesmos passos (sem gravar as capturas no repositório).
As imagens em `docs/screenshots/` são capturas reais deste ambiente.
