import type { LojaId, Monitoramento, NovoMonitoramento, ProdutoLoja, StatusServidor, Usuario } from './types'

export class ErroApi extends Error {
  readonly status: number

  constructor(status: number, mensagem: string) {
    super(mensagem)
    this.status = status
  }
}

const SERVIDOR_FORA = 'Não foi possível conectar ao servidor. O backend está rodando na porta 8080?'

// Só para respostas sem mensagem: 502/503/504 vazio é o proxy do Vite com o backend desligado
const MENSAGENS_PADRAO: Record<number, string> = {
  401: 'Sua sessão expirou. Entre novamente.',
  403: 'Sessão inválida. Recarregue a página e tente de novo.',
  404: 'Não encontrado.',
  502: SERVIDOR_FORA,
  503: SERVIDOR_FORA,
  504: SERVIDOR_FORA,
}

function tokenCsrf(): string | null {
  const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.split('=')[1]) : null
}

async function requisitar<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  const metodo = (init.method ?? 'GET').toUpperCase()
  const csrf = tokenCsrf()
  if (metodo !== 'GET' && csrf) {
    headers.set('X-XSRF-TOKEN', csrf)
  }

  let res: Response
  try {
    res = await fetch(url, { ...init, headers, credentials: 'same-origin' })
  } catch {
    throw new ErroApi(0, SERVIDOR_FORA)
  }
  if (!res.ok) {
    const corpo = await res.json().catch(() => null)
    throw new ErroApi(res.status, corpo?.message || MENSAGENS_PADRAO[res.status] || `Erro ${res.status}`)
  }
  return res.status === 204 ? (undefined as T) : res.json()
}

const json = (corpo: unknown): RequestInit => ({ method: 'POST', body: JSON.stringify(corpo) })

export const buscarStatus = () => requisitar<StatusServidor>('/api/status')
export const usuarioAtual = () => requisitar<Usuario>('/api/auth/eu')
export const entrar = (email: string, senha: string) => requisitar<Usuario>('/api/auth/login', json({ email, senha }))
export const criarConta = (nome: string, email: string, senha: string) =>
  requisitar<Usuario>('/api/auth/cadastro', json({ nome, email, senha }))
export const sair = () => requisitar<void>('/api/auth/logout', { method: 'POST' })

export const buscarNaLoja = (loja: LojaId, termo: string) =>
  requisitar<ProdutoLoja[]>(`/api/lojas/${loja}/busca?${new URLSearchParams({ termo })}`)
export const listarMonitoramentos = () => requisitar<Monitoramento[]>('/api/monitoramentos')
export const cadastrarMonitoramento = (novo: NovoMonitoramento) =>
  requisitar<Monitoramento>('/api/monitoramentos', json(novo))
export const verificarAgora = (id: number) =>
  requisitar<Monitoramento>(`/api/monitoramentos/${id}/verificar`, { method: 'POST' })
export const removerMonitoramento = (id: number) =>
  requisitar<void>(`/api/monitoramentos/${id}`, { method: 'DELETE' })
