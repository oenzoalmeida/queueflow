import { useState } from 'react'

type Props = React.InputHTMLAttributes<HTMLInputElement>

// Campo de senha com botão-olho (mostrar/ocultar). Só apresentação:
// repassa todas as props do input original e não altera validação nem envio.
export default function PasswordField(props: Props) {
  const [show, setShow] = useState(false)
  return (
    <div className="pw-field">
      <input {...props} type={show ? 'text' : 'password'} />
      <button
        type="button"
        className="pw-toggle"
        aria-label={show ? 'Ocultar senha' : 'Mostrar senha'}
        aria-pressed={show}
        onClick={() => setShow((v) => !v)}
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" />
          <circle cx="12" cy="12" r="3" />
          {show && <line x1="4" y1="20" x2="20" y2="4" />}
        </svg>
      </button>
    </div>
  )
}
