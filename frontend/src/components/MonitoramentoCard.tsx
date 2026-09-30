import { useState } from 'react'
import { formatarDataHora, formatarPreco } from '../formatar'
import type { Monitoramento } from '../types'

interface Props {
  monitoramento: Monitoramento
  emailConta: string
  emailConfigurado: boolean
  onVerificar: (id: number) => Promise<void>
  onRemover: (id: number) => Promise<void>
}

type Tom = 'neutro' | 'aguardando' | 'sucesso' | 'alerta' | 'erro'

interface Situacao {
  tom: Tom
  titulo: string
  detalhe?: string
}

function situacao(m: Monitoramento, emailConta: string, emailConfigurado: boolean): Situacao {
  if (m.precoPixAtual == null) {
    return { tom: 'neutro', titulo: 'Aguardando a primeira verificação' }
  }
  if (!m.disponivel) {
    return { tom: 'alerta', titulo: 'Produto indisponível na loja', detalhe: 'Avisaremos quando voltar dentro do seu preço.' }
  }

  const falta = m.precoPixAtual - m.precoMaximo
  if (falta > 0) {
    const percentual = (falta / m.precoPixAtual) * 100
    return {
      tom: 'aguardando',
      titulo: `Aguardando: faltam ${formatarPreco(falta)} (${percentual.toFixed(1).replace('.', ',')}%)`,
      detalhe: `O aviso sai quando o preço à vista chegar a ${formatarPreco(m.precoMaximo)}.`,
    }
  }

  switch (m.ultimoAvisoResultado) {
    case 'ENVIADO':
      return {
        tom: 'sucesso',
        titulo: 'Preço atingido · aviso enviado',
        detalhe: `E-mail enviado para ${emailConta} em ${formatarDataHora(m.ultimoAvisoEm)}.`,
      }
    case 'FALHOU':
      return {
        tom: 'erro',
        titulo: 'Preço atingido · o e-mail falhou',
        detalhe: 'O servidor de e-mail recusou o envio. Tentaremos de novo na próxima verificação.',
      }
    case 'EMAIL_NAO_CONFIGURADO':
    default:
      return {
        tom: 'alerta',
        titulo: 'Preço atingido · e-mail não enviado',
        detalhe: emailConfigurado
          ? 'O envio será tentado de novo na próxima verificação.'
          : 'O envio de e-mail não está configurado no servidor. Veja o README.',
      }
  }
}

export function MonitoramentoCard({ monitoramento: m, emailConta, emailConfigurado, onVerificar, onRemover }: Props) {
  const [ocupado, setOcupado] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const s = situacao(m, emailConta, emailConfigurado)
  const progresso =
    m.precoPixAtual != null ? Math.min(100, Math.round((m.precoMaximo / m.precoPixAtual) * 100)) : 0

  async function executar(acao: (id: number) => Promise<void>, manterMontado: boolean) {
    setOcupado(true)
    setErro(null)
    try {
      await acao(m.id)
      if (manterMontado) setOcupado(false)
    } catch (err) {
      setErro((err as Error).message)
      setOcupado(false)
    }
  }

  return (
    <article className="card">
      {m.imagem && <img src={m.imagem} alt="" loading="lazy" />}
      <div className="card-corpo">
        <span className="loja-tag">{m.lojaNome}</span>
        <a href={m.link} target="_blank" rel="noreferrer" className="card-titulo">
          {m.nome}
        </a>
        <div className="card-precos">
          <span className={`preco tom-${s.tom}`}>{formatarPreco(m.precoPixAtual)}</span>
          <span className="suave">à vista · {formatarPreco(m.precoAtual)} normal</span>
        </div>
        <p className="suave">
          Seu preço: até <strong>{formatarPreco(m.precoMaximo)}</strong>
        </p>

        <div className={`situacao tom-${s.tom}`}>
          <strong>{s.titulo}</strong>
          {s.detalhe && <span>{s.detalhe}</span>}
          {s.tom === 'aguardando' && (
            <div
              className="barra"
              role="progressbar"
              aria-valuenow={progresso}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label="Quanto o preço já se aproximou do seu"
            >
              <div style={{ width: `${progresso}%` }} />
            </div>
          )}
        </div>

        <p className="suave pequeno">Verificado em {formatarDataHora(m.ultimaVerificacao)}</p>
        {erro && <p className="erro">{erro}</p>}
        <div className="card-acoes">
          <button type="button" onClick={() => executar(onVerificar, true)} disabled={ocupado}>
            Verificar agora
          </button>
          <button type="button" className="secundario" onClick={() => executar(onRemover, false)} disabled={ocupado}>
            Remover
          </button>
        </div>
      </div>
    </article>
  )
}
