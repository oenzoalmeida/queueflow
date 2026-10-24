# Deploy do backend no Cloud Run (plano aprovado, aguardando billing)

Imagem: `ghcr.io/oenzoalmeida/queueflow-backend:koyeb` (workflow `build-image.yml`, pública).
Config: request-based, 512Mi/1vCPU, min 0 / max 1, us-central1, timeout 900s (WebSocket é requisição longa), concurrency 80, porta 8080, health `/api/public/health`.
Envs de runtime: SPRING_PROFILES_ACTIVE=prod, SPRING_DATASOURCE_URL/USERNAME/PASSWORD (Aiven, sslmode=require), JWT_SECRET, FRONTEND_URL (origens Firebase), QUEUEFLOW_BOOTSTRAP_*=off.
Testes obrigatórios: health, REST público, login JWT, WebSocket (totem/display), reconexão WS após queda, criação/chamada de senha fim a fim, CORS a partir do frontend Firebase, cold start medido, wake pós scale-to-zero, memória < 512Mi sob carga leve, estabilidade 24h.
## Proteção de custo (obrigatória antes do primeiro deploy)

- Budget alerts-only mensal de US$ 5 no projeto, com alertas em US$ 1, 50%, 90% e 100%. Budget NÃO é hard cap.
- Corte automático real: Spend Cap budget (Cloud Billing, preview) sobre o Cloud Run do projeto. Ao atingir o valor, o budget entra em estado "Enforced" e o tráfego do serviço é pausado automaticamente até suspensão manual. Aplica-se a um projeto e um serviço elegível (Cloud Run é elegível).
- Redundância: notificação programática Pub/Sub do budget acionando Cloud Function que remove a rotação pública do serviço se o gasto reportado exceder o limite (mecanismo documentado pelo Cloud Billing).
- Limites do serviço: request-based billing, min instances 0, max instances 1, sem VPC, região us-central1, timeout explícito, 512 MiB / 1 vCPU.
- Riscos residuais: latência de reporte de custo (até algumas horas; o spend cap pode disparar depois do excesso curto), e pausa do serviço interrompe a demo até liberação manual. Custo já registrado não é revertido.

## Sequência de deploy (após autorização de billing)

1. Criar projeto `enzo-java-backends`, vincular billing, configurar budget + spend cap + alertas.
2. `gcloud run deploy <servico> --image <imagem> --region us-central1 --memory 512Mi --cpu 1 --min-instances 0 --max-instances 1 --timeout <t> --no-allow-unauthenticated` (ou com invocação pública conforme o app).
3. Configurar envs do runtime (nomes abaixo; valores vêm do Render/secret manager no dia).
4. Executar o plano de testes; só depois trocar URLs no frontend/perfil e desligar o serviço Render correspondente.
5. Rollback: manter o serviço Render antigo ligado até a validação completa; o Cloud Run guarda revisões anteriores (`gcloud run services update-traffic --revisions`).
