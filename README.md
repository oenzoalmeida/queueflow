# QueueFlow

Sistema full stack para gerenciamento de filas de atendimento em tempo real, com painel de acompanhamento, autenticação e atualização instantânea dos atendimentos.

## Demonstração

[Acessar o QueueFlow online](https://queueflow-frontend.onrender.com)

> Para uso normal, compartilhe apenas o link acima. O backend é um serviço técnico utilizado pelo frontend.

## Sobre o projeto

O QueueFlow foi desenvolvido para organizar o fluxo de atendimento de estabelecimentos, permitindo controlar senhas, guichês e o andamento da fila por meio de uma aplicação web.

O projeto utiliza uma arquitetura separada entre frontend e backend, com comunicação em tempo real via WebSocket.

## Perfis de acesso

- **Administrador (ADMIN):** acesso ao painel administrativo e à configuração da operação.
- **Atendente (ATTENDANT):** acesso à tela operacional de atendimento.
- **Totem e Display:** rotas públicas para emissão e exibição de senhas, sem necessidade de login.

**Conta demo (atendente):** `demo@queueflow.app` / `Demo@2026`

> O acesso administrativo é provisionado internamente via bootstrap do backend (variáveis `QUEUEFLOW_BOOTSTRAP_*`) e não possui credencial pública.

## Principais recursos

- Gerenciamento de filas e senhas
- Controle de guichês de atendimento
- Painel com informações da operação
- Atualizações em tempo real com WebSocket
- Autenticação e autorização com JWT
- Persistência de dados em PostgreSQL
- Validação de dados e tratamento centralizado de erros
- Estrutura preparada para execução com Docker

## Tecnologias

### Frontend

- React
- TypeScript
- Vite
- React Router
- Axios
- STOMP / SockJS
- Lucide React

### Backend

- Java 21
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Security
- Spring WebSocket
- JWT
- Flyway
- PostgreSQL

### Infraestrutura

- Docker
- Docker Compose
- Render

## Estrutura do projeto

```text
queueflow/
├── backend/          API Spring Boot
├── frontend/         Aplicação React + TypeScript
├── docker-compose.yml
├── Dockerfile
├── render.yaml
├── netlify.toml
└── DEPLOY.md
```

## Executando localmente

O projeto possui arquivos de configuração de ambiente de exemplo em `backend/.env.example` e `frontend/.env.example`.

Também é possível utilizar o `docker-compose.yml` disponível na raiz para subir os serviços necessários ao ambiente local.

Consulte o arquivo `DEPLOY.md` para detalhes adicionais sobre publicação e configuração dos ambientes.

## Segurança e privacidade

- Autenticação por JWT e autorização por papel: telas administrativas exigem `ADMIN` e a tela operacional exige `ATTENDANT`.
- Senhas armazenadas apenas como hash; a conta administrativa é provisionada internamente pelo bootstrap do backend, sem credencial pública.
- Totem e display são públicos por design e não coletam dados pessoais dos clientes atendidos.
- Páginas de [Termos de Uso](https://queueflow-frontend.onrender.com/termos) e [Política de Privacidade](https://queueflow-frontend.onrender.com/privacidade) disponíveis no rodapé do login.

## Testes

- Backend: testes automatizados (integração e política de chamada de senhas) executados com `./mvnw test` e validados no CI a cada push.

## Deploy

- Frontend (produção): <https://queueflow-frontend.onrender.com>
- Backend (produção): <https://queueflow-backend-is0i.onrender.com>
- O `render.yaml` provisiona os serviços no Render; o fluxo alternativo com Netlify (`netlify.toml`) está descrito no `DEPLOY.md`.

## Limitações conhecidas

- A demonstração roda em infraestrutura gratuita: os serviços hibernam após inatividade (primeiro acesso fica lento) e os dados podem ser redefinidos.
- O projeto atende a um único estabelecimento por instância.
- O frontend não possui testes automatizados; é validado por build no CI.

## Autor

Enzo Almeida
