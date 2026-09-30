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
import { FaixaConfirmacao } from './components/FaixaConfirmacao'
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
  // Resultado do link de confirmação, que volta como ?email=confirmado ou ?email=link-invalido
  const [retornoEmail] = useState(() => new URLSearchParams(window.location.search).get('email'))

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
    if (retornoEmail) window.history.replaceState(null, '', window.location.pathname)
  }, [retornoEmail])

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
        <div className="marca">
          descontos <span>Black Friday</span>
        </div>
        {usuario && (
          <div className="conta">
            <span className="ola" title={usuario.email}>
              Olá, <strong>{usuario.nome}</strong>
            </span>
            <button type="button" className="secundario" onClick={encerrarSessao}>
              Sair
            </button>
          </div>
        )}
      </header>

      <section className="destaque">
        <h1>{usuario ? 'O que você quer monitorar hoje?' : 'Compre na hora certa. A gente avisa quando o preço cair.'}</h1>
        <p>Escolha o produto, defina quanto quer pagar e receba um e-mail quando o preço chegar lá.</p>

        {retornoEmail === 'confirmado' && (
          <div className="faixa-sucesso">E-mail confirmado! Os avisos de preço já estão liberados.</div>
        )}
        {retornoEmail === 'link-invalido' && (
          <div className="faixa-erro">
            Este link de confirmação é inválido ou já foi usado. Entre na sua conta e peça um novo e-mail.
          </div>
        )}
        {usuario && !usuario.emailConfirmado && <FaixaConfirmacao email={usuario.email} />}

        {usuario === undefined && <p className="suave centro">Carregando...</p>}

        {usuario === null && <TelaAcesso onEntrou={setUsuario} />}

        {usuario && status && (
          <>
            {!status.emailConfigurado && (
              <div className="faixa-alerta">
                O envio de e-mail ainda não está configurado no servidor: os avisos ficam registrados, mas não
                chegam à sua caixa de entrada. Veja “Configurar o envio de e-mail” no README.
              </div>
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
          </>
        )}
      </section>

      {usuario === null && (
        <section className="vantagens">
          <article>
            <span className="icone" aria-hidden="true">1</span>
            <h3>Escolha o produto exato</h3>
            <p>Busque na loja e selecione o modelo que você quer, sem confundir com parecidos.</p>
          </article>
          <article>
            <span className="icone" aria-hidden="true">2</span>
            <h3>Defina o seu preço</h3>
            <p>Diga quanto aceita pagar à vista. O app confere o preço a cada hora.</p>
          </article>
          <article>
            <span className="icone" aria-hidden="true">3</span>
            <h3>Receba no e-mail</h3>
            <p>Quando o preço chegar lá, você recebe o aviso com o link direto da loja.</p>
          </article>
        </section>
      )}

      {usuario && status && (
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
