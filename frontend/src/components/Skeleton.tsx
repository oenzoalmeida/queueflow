import type { CSSProperties } from 'react'

/** Bloco de esqueleto: placeholder cinza no formato do conteúdo que está chegando. */
export function Skeleton({ style, className = '' }: { style?: CSSProperties; className?: string }) {
  return <span aria-hidden="true" className={`skeleton ${className}`} style={style} />
}

function StatCardSkeleton() {
  return (
    <div className="card stat">
      <Skeleton style={{ width: 56, height: 26 }} />
      <Skeleton style={{ width: 96, height: 13 }} />
    </div>
  )
}

/** Esqueleto genérico de página inteira (gate de autenticação). */
export function PageSkeleton() {
  return (
    <div role="status" className="page-skeleton">
      <span className="sr-only">Carregando…</span>
      <Skeleton style={{ width: 180, height: 28, marginBottom: 18 }} />
      <div className="stat-grid">
        {[0, 1, 2, 3].map((i) => <StatCardSkeleton key={i} />)}
      </div>
      <div className="card">
        <Skeleton style={{ width: '42%', height: 16, marginBottom: 14 }} />
        <Skeleton style={{ width: '100%', height: 14, marginBottom: 10 }} />
        <Skeleton style={{ width: '100%', height: 14, marginBottom: 10 }} />
        <Skeleton style={{ width: '68%', height: 14 }} />
      </div>
    </div>
  )
}

/** Esqueleto do Dashboard: espelha stat-grid, gráficos em duas colunas e tabela. */
export function DashboardSkeleton() {
  return (
    <div role="status">
      <span className="sr-only">Carregando…</span>
      <div className="stat-grid">
        {[0, 1, 2, 3, 4, 5, 6].map((i) => <StatCardSkeleton key={i} />)}
      </div>
      <div className="two-col">
        {[0, 1].map((i) => (
          <section key={i} className="card">
            <Skeleton style={{ width: 170, height: 18, marginBottom: 16 }} />
            <Skeleton style={{ width: '100%', height: 120 }} />
          </section>
        ))}
      </div>
      <section className="card">
        <Skeleton style={{ width: 130, height: 18, marginBottom: 16 }} />
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} style={{ width: '100%', height: 14, marginBottom: 12 }} />
        ))}
      </section>
    </div>
  )
}

/** Esqueleto das opções do Totem: cartões no formato dos botões de fila. */
export function TotemSkeleton() {
  return (
    <div role="status" style={{ width: '100%' }}>
      <span className="sr-only">Carregando opções de atendimento…</span>
      <div className="totem-grid">
        {[0, 1, 2].map((i) => (
          <div key={i} className="totem-card skeleton-card" aria-hidden="true">
            <Skeleton style={{ width: 44, height: 30 }} />
            <Skeleton style={{ width: 130, height: 20 }} />
          </div>
        ))}
      </div>
    </div>
  )
}
