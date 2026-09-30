import { useState, type FormEvent } from 'react'
import { redefinirSenha } from '../api'

interface Props {
  token: string
  onConcluido: () => void
}

export function TelaNovaSenha({ token, onConcluido }: Props) {
  const [senha, setSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  async function enviar(e: FormEvent) {
    e.preventDefault()
    if (senha !== confirmacao) {
      setErro('As duas senhas não são iguais.')
      return
    }
    setEnviando(true)
    setErro(null)
    try {
      await redefinirSenha(token, senha)
      onConcluido()
    } catch (err) {
      setErro((err as Error).message)
      setEnviando(false)
    }
  }

  return (
    <section className="painel acesso">
      <h2>Criar nova senha</h2>
      <form className="form" onSubmit={enviar}>
        <label>
          Nova senha
          <input
            type="password"
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            autoComplete="new-password"
            minLength={8}
            maxLength={72}
            required
          />
          <small className="suave">Mínimo de 8 caracteres.</small>
        </label>
        <label>
          Repita a nova senha
          <input
            type="password"
            value={confirmacao}
            onChange={(e) => setConfirmacao(e.target.value)}
            autoComplete="new-password"
            maxLength={72}
            required
          />
        </label>
        {erro && <p className="erro">{erro}</p>}
        <button type="submit" disabled={enviando}>
          {enviando ? 'Salvando...' : 'Salvar nova senha'}
        </button>
      </form>
    </section>
  )
}
