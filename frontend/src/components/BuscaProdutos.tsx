import { useState, type FormEvent } from 'react'
import { buscarNaLoja } from '../api'
import { formatarPreco } from '../formatar'
import type { LojaId, LojaStatus, ProdutoLoja } from '../types'

const POR_PAGINA = 8

interface Props {
  lojas: LojaStatus[]
  selecionado: ProdutoLoja | null
  onSelecionar: (produto: ProdutoLoja) => void
}

export function BuscaProdutos({ lojas, selecionado, onSelecionar }: Props) {
  const [loja, setLoja] = useState<LojaId>(() => lojas.find((l) => l.ativa)?.id ?? 'KABUM')
  const [termo, setTermo] = useState('')
  const [resultados, setResultados] = useState<ProdutoLoja[] | null>(null)
  const [buscando, setBuscando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [limite, setLimite] = useState(POR_PAGINA)

  const nomeLoja = lojas.find((l) => l.id === loja)?.nome ?? loja

  async function buscar(e: FormEvent) {
    e.preventDefault()
    setBuscando(true)
    setErro(null)
    setLimite(POR_PAGINA)
    try {
      setResultados(await buscarNaLoja(loja, termo))
    } catch (err) {
      setResultados(null)
      setErro((err as Error).message)
    } finally {
      setBuscando(false)
    }
  }

  const ehSelecionado = (p: ProdutoLoja) => p.loja === selecionado?.loja && p.codigo === selecionado?.codigo

  return (
    <section className="painel">
      <h2>1. Encontre o produto</h2>
      <form className="busca" onSubmit={buscar}>
        <select value={loja} onChange={(e) => setLoja(e.target.value as LojaId)} aria-label="Loja">
          {lojas.map((l) => (
            <option key={l.id} value={l.id} disabled={!l.ativa}>
              {l.nome}
              {!l.ativa && ' (em breve)'}
            </option>
          ))}
        </select>
        <input
          placeholder="Ex.: fone bluetooth jbl"
          aria-label={`Buscar no ${nomeLoja}`}
          value={termo}
          onChange={(e) => setTermo(e.target.value)}
          maxLength={100}
          required
        />
        <button type="submit" disabled={buscando}>
          {buscando ? 'Buscando...' : 'Buscar'}
        </button>
      </form>

      {erro && <p className="erro">{erro}</p>}
      {resultados?.length === 0 && <p className="suave">Nenhum produto encontrado.</p>}

      {resultados && resultados.length > 0 && (
        <ul className="resultados">
          {resultados.slice(0, limite).map((p) => (
            <li key={`${p.loja}-${p.codigo}`} className={ehSelecionado(p) ? 'ativo' : undefined}>
              {p.imagem ? <img src={p.imagem} alt="" loading="lazy" /> : <span className="sem-imagem" />}
              <div className="resultado-info">
                <a href={p.link} target="_blank" rel="noreferrer">
                  {p.nome}
                </a>
                <span className="precos">
                  <span>
                    <strong>{formatarPreco(p.precoPix)}</strong> à vista
                  </span>
                  <span>{formatarPreco(p.preco)} no cartão</span>
                  {!p.disponivel && <em className="indisponivel">indisponível</em>}
                </span>
              </div>
              <button type="button" onClick={() => onSelecionar(p)}>
                {ehSelecionado(p) ? 'Selecionado' : 'Selecionar'}
              </button>
            </li>
          ))}
        </ul>
      )}
      {resultados && resultados.length > limite && (
        <button type="button" className="secundario mais" onClick={() => setLimite(limite + POR_PAGINA)}>
          Mostrar mais ({resultados.length - limite})
        </button>
      )}
    </section>
  )
}
