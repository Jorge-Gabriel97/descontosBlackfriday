import { useState } from 'react'
import { reenviarConfirmacao } from '../api'

export function FaixaConfirmacao({ email }: { email: string }) {
  const [enviando, setEnviando] = useState(false)
  const [resposta, setResposta] = useState<string | null>(null)

  async function reenviar() {
    setEnviando(true)
    try {
      await reenviarConfirmacao()
      setResposta('E-mail reenviado. Confira também a caixa de spam.')
    } catch (err) {
      setResposta((err as Error).message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="faixa-alerta">
      <p>
        <strong>Confirme seu e-mail.</strong> Enviamos um link para {email}. Até lá, os avisos de preço ficam
        retidos.
        {resposta && <span className="faixa-resposta">{resposta}</span>}
      </p>
      <button type="button" className="secundario" onClick={reenviar} disabled={enviando}>
        {enviando ? 'Enviando...' : 'Reenviar e-mail'}
      </button>
    </div>
  )
}
