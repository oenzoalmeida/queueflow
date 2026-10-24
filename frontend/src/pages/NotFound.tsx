import { Link } from 'react-router-dom'
import { usePageTitle } from '../usePageTitle'

export default function NotFound() {
  usePageTitle('Página não encontrada')
  return (
    <div className="login-page">
      <div className="login-card">
        <h1>404</h1>
        <p className="muted">Página não encontrada.</p>
        <p className="muted">O endereço acessado não existe ou foi movido.</p>
        <Link className="btn primary" to="/">
          Voltar ao início
        </Link>
      </div>
    </div>
  )
}
