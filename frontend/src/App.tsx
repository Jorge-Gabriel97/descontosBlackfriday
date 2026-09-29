import { useCallback, useEffect, useState } from 'react'
import {
  buscarStatus,
  cadastrarMonitoramento,
  ErroApi,
  listarMonitoramentos,
  removerMonitoramento,
  sair,
  usuarioAtual,
  verificarAgora,
} from './api'
import { BuscaProdutos } from './components/BuscaProdutos'
import { FormMonitoramento } from './components/FormMonitoramento'
import { MonitoramentoCard } from './components/MonitoramentoCard'
import { TelaAcesso } from './components/TelaAcesso'
import type { Monitoramento, NovoMonitoramento, ProdutoLoja, StatusServidor, Usuario } from './types'

function App() {
  // undefined = ainda carregando; null = não logado
  const [usuario, setUsuario] = useState<Usuario | null | undefined>(undefined)
  const [status, setStatus] = useState<StatusServidor | null>(null)
  const [selecionado, setSelecionado] = useState<ProdutoLoja | null>(null)
  const [monitoramentos, setMonitoramentos] = useState<Monitoramento[]>([])
  const [erro, setErro] = useState<string | null>(null)

  // O /api/status também entrega o cookie CSRF usado no login
  const carregarStatus = useCallback(
    () => buscarStatus().then(setStatus, (err: Error) => setErro(err.message)),
    [],
  )

  const tratarErro = useCallback((err: unknown) => {
    if (err instanceof ErroApi && err.status === 401) {
      setUsuario(null)
    } else {
      setErro((err as Error).message)
    }
  }, [])

  const carregarMonitoramentos = useCallback(
    () =>
      listarMonitoramentos().then((lista) => {
        setMonitoramentos(lista)
        setErro(null)
      }, tratarErro),
    [tratarErro],
  )

  useEffect(() => {
    carregarStatus()
    usuarioAtual().then(setUsuario, () => setUsuario(null))
  }, [carregarStatus])

  useEffect(() => {
    if (usuario) carregarMonitoramentos()
  }, [usuario, carregarMonitoramentos])

  async function cadastrar(novo: NovoMonitoramento) {
    try {
      await cadastrarMonitoramento(novo)
    } catch (err) {
      if (err instanceof ErroApi && err.status === 401) setUsuario(null)
      throw err
    }
    setSelecionado(null)
    await carregarMonitoramentos()
  }

  async function verificar(id: number) {
    const atualizado = await verificarAgora(id)
    setMonitoramentos((lista) => lista.map((m) => (m.id === id ? atualizado : m)))
  }

  async function remover(id: number) {
    await removerMonitoramento(id)
    setMonitoramentos((lista) => lista.filter((m) => m.id !== id))
  }

  async function encerrarSessao() {
    await sair().catch(() => undefined)
    setUsuario(null)
    setMonitoramentos([])
    setSelecionado(null)
    // O logout apaga o token CSRF; busca um novo para o próximo login
    await carregarStatus()
  }

  return (
    <div className="app">
      <header className="topo">
        {usuario && (
          <div className="conta">
            <span className="suave">
              Olá, <strong>{usuario.nome}</strong> · {usuario.email}
            </span>
            <button type="button" className="secundario" onClick={encerrarSessao}>
              Sair
            </button>
          </div>
        )}
        <h1>
          Descontos <span>Black Friday</span>
        </h1>
        <p>Escolha um produto, defina seu preço e receba um e-mail quando ele chegar lá.</p>
      </header>

      {usuario === undefined && <p className="suave centro">Carregando...</p>}

      {usuario === null && <TelaAcesso onEntrou={setUsuario} />}

      {usuario && status && (
        <>
          {!status.emailConfigurado && (
            <p className="faixa-alerta">
              O envio de e-mail ainda não está configurado no servidor: os avisos ficam registrados, mas não
              chegam à sua caixa de entrada. Veja “Configurar o envio de e-mail” no README.
            </p>
          )}

          <div className="etapas">
            <BuscaProdutos lojas={status.lojas} selecionado={selecionado} onSelecionar={setSelecionado} />
            <FormMonitoramento
              key={selecionado ? `${selecionado.loja}-${selecionado.codigo}` : 'nenhum'}
              produto={selecionado}
              emailConta={usuario.email}
              onSalvar={cadastrar}
            />
          </div>

          <section className="monitorados">
            <h2>Seus produtos monitorados</h2>
            {erro && <p className="erro">{erro}</p>}
            {!erro && monitoramentos.length === 0 && <p className="suave">Nenhum produto monitorado ainda.</p>}
            <div className="grade">
              {monitoramentos.map((m) => (
                <MonitoramentoCard
                  key={m.id}
                  monitoramento={m}
                  emailConta={usuario.email}
                  emailConfigurado={status.emailConfigurado}
                  onVerificar={verificar}
                  onRemover={remover}
                />
              ))}
            </div>
          </section>
        </>
      )}

      {usuario && !status && erro && <p className="erro centro">{erro}</p>}

      <footer className="rodape">
        Os preços são verificados automaticamente a cada hora. Monitor independente, sem vínculo com as lojas.
        Os preços valem apenas conforme a página do produto no site de cada loja.
      </footer>
    </div>
  )
}

export default App
