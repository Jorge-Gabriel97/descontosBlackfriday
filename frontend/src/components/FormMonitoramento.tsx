import { useState, type FormEvent } from 'react'
import { formatarPreco } from '../formatar'
import type { NovoMonitoramento, ProdutoLoja } from '../types'

interface Props {
  produto: ProdutoLoja | null
  emailConta: string
  onSalvar: (novo: NovoMonitoramento) => Promise<void>
}

export function FormMonitoramento({ produto, emailConta, onSalvar }: Props) {
  // Calculado só na montagem: o App troca a key ao selecionar outro produto
  const [precoMaximo, setPrecoMaximo] = useState(() =>
    produto ? (Math.floor(produto.precoPix * 0.9 * 100) / 100).toFixed(2) : '',
  )
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  async function enviar(e: FormEvent) {
    e.preventDefault()
    if (!produto) return
    setSalvando(true)
    setErro(null)
    try {
      await onSalvar({ loja: produto.loja, codigoProduto: produto.codigo, precoMaximo: Number(precoMaximo) })
    } catch (err) {
      setErro((err as Error).message)
      setSalvando(false)
    }
  }

  return (
    <section className="painel">
      <h2>2. Defina seu preço</h2>
      {!produto ? (
        <p className="suave">Selecione um produto na busca.</p>
      ) : (
        <form className="form" onSubmit={enviar}>
          <div className="produto-escolhido">
            <strong>{produto.nome}</strong>
            <span>
              Hoje: {formatarPreco(produto.precoPix)} à vista · {formatarPreco(produto.preco)}
            </span>
          </div>
          <label>
            Me avise quando o preço à vista for até (R$)
            <input
              type="number"
              min={0.01}
              step={0.01}
              value={precoMaximo}
              onChange={(e) => setPrecoMaximo(e.target.value)}
              required
            />
          </label>
          <p className="suave pequeno">
            O aviso será enviado para <strong>{emailConta}</strong>.
          </p>
          {erro && <p className="erro">{erro}</p>}
          <button type="submit" disabled={salvando}>
            {salvando ? 'Consultando a loja...' : 'Monitorar produto'}
          </button>
        </form>
      )}
    </section>
  )
}
