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

**Contas demo:**

| Perfil | E-mail | Senha |
| --- | --- | --- |
| Administrador (ADMIN) | `admin@queueflow.app` | `Admin@Portfolio2026` |
| Atendente (ATTENDANT) | `demo@queueflow.app` | `Demo@2026` |

> Essas contas são provisionadas pelo bootstrap do backend (variáveis `QUEUEFLOW_BOOTSTRAP_*` no Render) e servem apenas para demonstração do portfólio.

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

## Autor

Enzo Almeida
