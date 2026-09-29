import { useState, type FormEvent } from 'react'
import { criarConta, entrar } from '../api'
import type { Usuario } from '../types'

interface Props {
  onEntrou: (usuario: Usuario) => void
}

export function TelaAcesso({ onEntrou }: Props) {
  const [modo, setModo] = useState<'entrar' | 'criar'>('entrar')
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const criando = modo === 'criar'

  async function enviar(e: FormEvent) {
    e.preventDefault()
    setEnviando(true)
    setErro(null)
    try {
      onEntrou(criando ? await criarConta(nome.trim(), email.trim(), senha) : await entrar(email.trim(), senha))
    } catch (err) {
      setErro((err as Error).message)
      setEnviando(false)
    }
  }

  function trocarModo() {
    setModo(criando ? 'entrar' : 'criar')
    setErro(null)
  }

  return (
    <section className="painel acesso">
      <h2>{criando ? 'Criar conta' : 'Entrar'}</h2>
      <form className="form" onSubmit={enviar}>
        {criando && (
          <label>
            Nome
            <input value={nome} onChange={(e) => setNome(e.target.value)} autoComplete="name" maxLength={100} required />
          </label>
        )}
        <label>
          E-mail
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
        </label>
        <label>
          Senha
          <input
            type="password"
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            autoComplete={criando ? 'new-password' : 'current-password'}
            minLength={criando ? 8 : undefined}
            maxLength={72}
            required
          />
          {criando && <small className="suave">Mínimo de 8 caracteres.</small>}
        </label>
        {criando && (
          <p className="suave pequeno">Os avisos de preço serão enviados para este e-mail.</p>
        )}
        {erro && <p className="erro">{erro}</p>}
        <button type="submit" disabled={enviando}>
          {enviando ? 'Aguarde...' : criando ? 'Criar conta' : 'Entrar'}
        </button>
      </form>
      <p className="suave troca-modo">
        {criando ? 'Já tem conta?' : 'Ainda não tem conta?'}{' '}
        <button type="button" className="link" onClick={trocarModo}>
          {criando ? 'Entrar' : 'Criar conta'}
        </button>
      </p>
    </section>
  )
}
