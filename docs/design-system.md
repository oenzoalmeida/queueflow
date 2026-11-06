# Design System do QueueFlow

O design system do QueueFlow é implementado em CSS puro (`frontend/src/styles.css`),
sem biblioteca de componentes. Esta página documenta os tokens e os padrões
reutilizáveis para manter as telas consistentes.

## Tokens

Definidos em `:root` como custom properties:

| Token | Valor | Uso |
| --- | --- | --- |
| `--bg` | `#f4f6fb` | Fundo das telas internas |
| `--panel` | `#ffffff` | Superfície de cards e formulários |
| `--line` | `#e3e8f0` | Bordas e divisores |
| `--text` | `#16233b` | Texto principal |
| `--muted` | `#64748b` | Texto secundário, labels, estados vazios |
| `--primary` | `#2563eb` | Ação principal, links, destaques |
| `--primary-dark` | `#1d4ed8` | Hover da ação principal, títulos de marca |
| `--danger` | `#dc2626` | Ações destrutivas (ausente, excluir) |
| `--green` | `#16a34a` | Sucesso, status "ativo", tempo real conectado |
| `--amber` | `#d97706` | Prioridade, alertas intermediários |
| `--radius` | `10px` | Raio padrão de cards, botões e inputs |
| `--shadow` | `0 1px 3px rgba(22,35,59,.08)` | Sombra de cards |
| `--focus-ring` | `#bfdbfe` | Anel de foco visível (teclado) |

Tipografia: pilha do sistema (`'Segoe UI', system-ui, sans-serif`), corpo 15px.
Códigos de senha usam a classe `.mono` (monoespaçada, peso 600).

## Componentes

- **Botões (`.btn`)**: variantes `.primary`, `.danger`, `.ghost` e tamanhos
  `.small`, `.huge`. Estado desabilitado com opacidade 0.55; todo botão que dispara
  requisição fica `disabled` enquanto a operação está em voo (previne duplo envio).
- **Cards (`.card`)**: modificadores `.narrow` (formulários) e `.center`.
- **Tags (`.tag`)**: `.green`, `.gray`, `.blue`, `.amber` para status de senha,
  guichê e usuário.
- **Alertas (`.alert`)**: `.error` e `.success`, sempre com `role="alert"`.
- **Formulários**: `.inline-form` (criação rápida), `.grid-form` (cadastros),
  `.filter-form` (histórico). Labels com 13px em `--muted`.
- **Estatísticas (`.stat-grid` / `.stat`)**: indicadores do dashboard.
- **Gráfico de barras (`.bars`)**: volume por hora, CSS puro.

## Telas especiais

- **Totem (`.totem-*`)**: alvos de toque grandes (cartões com min-height 200px,
  botões `.big-btn`), tipografia ampla, sem navegação por teclado obrigatória mas
  totalmente operável por ela (cartões são `<button>` com `aria-pressed`).
- **Display público (`.display-*`)**: fundo escuro `#0b1526` para contraste em TV,
  senha atual em `clamp(110px, 24vw, 340px)` para leitura à distância, guichê em
  caixa alta, histórico das últimas 5 chamadas e indicador de conexão em tempo real
  (`.ws-dot`, com `role="status"`). Aviso sonoro via WebAudio ativado por gesto do
  usuário (`.sound-btn`), respeitando a política de autoplay dos navegadores.
- **Atendente (`.attendant-*`, `.code-xl`)**: senha atual em
  `clamp(48px, 10vw, 88px)`, uma ação primária por estado da senha.

## Acessibilidade

- Foco visível consistente (`:focus-visible` com `--focus-ring`) em botões,
  cartões do totem, links e itens do menu lateral; em inputs o anel acompanha
  `:focus` (digitação).
- Navegação por teclado: menu mobile com `aria-expanded`/`aria-controls`,
  cartões do totem com `aria-pressed`, alertas com `role="alert"`.
- Responsividade: breakpoints em 900px (menu lateral vira drawer) e 560px
  (totem em coluna única, ações do atendente em largura total).
