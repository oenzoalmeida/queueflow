import { useEffect, useState } from 'react'
import { api, errMessage } from '../api/client'

export default function Settings() {
  const [value, setValue] = useState<number>(2)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    api.get('/settings/priority').then((r) => setValue(r.data.normalsBeforePriority)).catch(() => setError('Falha ao carregar configurações.'))
  }, [])

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true); setError(null); setSaved(false)
    try {
      const r = await api.put('/settings/priority', { normalsBeforePriority: Number(value) })
      setValue(r.data.normalsBeforePriority)
      setSaved(true)
    } catch (err) {
      setError(errMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <h2>Configurações</h2>
      <section className="card narrow">
        <h3>Regra de prioridade</h3>
        <p className="muted">
          Quantidade de atendimentos normais chamados antes de uma senha prioritária.
          Exemplo: 2 normais → 1 prioritária. Se não houver prioritária aguardando,
          a fila continua chamando senhas normais normalmente.
        </p>
        {error && <div className="alert error">{error}</div>}
        {saved && !error && <div className="alert success">Configuração salva.</div>}
        <form className="inline-form" onSubmit={save}>
          <input
            type="number" min={0} max={99} value={value}
            onChange={(e) => setValue(Number(e.target.value))}
            style={{ maxWidth: 110 }}
          />
          <button className="btn primary" disabled={loading}>{loading ? 'Salvando…' : 'Salvar'}</button>
        </form>
      </section>
    </>
  )
}
