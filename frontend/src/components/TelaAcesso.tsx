import { useState, type FormEvent } from 'react'
import { criarConta, entrar, esqueciSenha } from '../api'
import type { Usuario } from '../types'

interface Props {
  onEntrou: (usuario: Usuario) => void
}

type Modo = 'entrar' | 'criar' | 'esqueci'

const TITULOS: Record<Modo, string> = { entrar: 'Entrar', criar: 'Criar conta', esqueci: 'Esqueci minha senha' }

export function TelaAcesso({ onEntrou }: Props) {
  const [modo, setModo] = useState<Modo>('entrar')
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [linkEnviado, setLinkEnviado] = useState(false)

  const criando = modo === 'criar'

  async function enviar(e: FormEvent) {
    e.preventDefault()
    setEnviando(true)
    setErro(null)
    try {
      if (modo === 'esqueci') {
        await esqueciSenha(email.trim())
        setLinkEnviado(true)
        setEnviando(false)
        return
      }
      onEntrou(criando ? await criarConta(nome.trim(), email.trim(), senha) : await entrar(email.trim(), senha))
    } catch (err) {
      setErro((err as Error).message)
      setEnviando(false)
    }
  }

  function irPara(novo: Modo) {
    setModo(novo)
    setErro(null)
    setLinkEnviado(false)
  }

  return (
    <section className="painel acesso">
      <h2>{TITULOS[modo]}</h2>

      {linkEnviado ? (
        <p className="suave">
          Se houver uma conta com <strong>{email.trim()}</strong>, enviamos um link para criar uma nova senha. Ele
          vale por 30 minutos. Confira também a caixa de spam.
        </p>
      ) : (
        <form className="form" onSubmit={enviar}>
          {modo === 'esqueci' && (
            <p className="suave pequeno">Informe o e-mail da sua conta e enviaremos um link para criar uma nova senha.</p>
          )}
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
          {modo !== 'esqueci' && (
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
          )}
          {modo === 'entrar' && (
            <button type="button" className="link esqueci" onClick={() => irPara('esqueci')}>
              Esqueci minha senha
            </button>
          )}
          {criando && (
            <p className="suave pequeno">Vamos enviar um link para confirmar este e-mail. Os avisos de preço chegam nele.</p>
          )}
          {erro && <p className="erro">{erro}</p>}
          <button type="submit" disabled={enviando}>
            {enviando ? 'Aguarde...' : modo === 'esqueci' ? 'Enviar link' : TITULOS[modo]}
          </button>
        </form>
      )}

      <p className="suave troca-modo">
        {modo === 'entrar' ? 'Ainda não tem conta?' : modo === 'criar' ? 'Já tem conta?' : 'Lembrou a senha?'}{' '}
        <button type="button" className="link" onClick={() => irPara(modo === 'entrar' ? 'criar' : 'entrar')}>
          {modo === 'entrar' ? 'Criar conta' : 'Entrar'}
        </button>
      </p>
    </section>
  )
}
