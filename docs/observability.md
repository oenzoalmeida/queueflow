# Observabilidade

## Logs estruturados (produção)

O profile `prod` emite logs em JSON no console via o encoder Logstash embutido
do Spring Boot (`logging.structured.format.console=logstash`), sem dependências
novas. O Azure App Service captura stdout, então cada linha vira um evento JSON
pesquisável (timestamp, nível, logger, mensagem, stack trace) no Log Stream e
no App Service Logs.

Para inspecionar: Portal Azure → App Service `queueflow-backend-oe` →
**Log stream** (tempo real) ou **Logs do aplicativo** (consulta).

Não há agente pago nem serviço novo: é apenas o formato da linha de log.

## Health check

`GET /api/public/health` (público) confirma que a API está de pé; usado nos
testes e2e e pode ser ligado ao **Health check** do App Service.

## WebSocket

O frontend exibe o estado da conexão em tempo real (indicador no painel e
banner "Sem conexão" com reconexão automática), o que cobre a observabilidade
visível para o operador sem ferramentas externas.
