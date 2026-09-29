export type LojaId = 'KABUM' | 'SHOPEE' | 'MERCADO_LIVRE' | 'AMAZON' | 'ALIEXPRESS'

export interface LojaStatus {
  id: LojaId
  nome: string
  ativa: boolean
}

export interface StatusServidor {
  emailConfigurado: boolean
  lojas: LojaStatus[]
}

export interface Usuario {
  id: number
  nome: string
  email: string
}

/** Produto como aparece na busca de uma loja. */
export interface ProdutoLoja {
  loja: LojaId
  codigo: string
  nome: string
  link: string
  imagem: string
  /** Preço normal (cartão/boleto). */
  preco: number
  /** Menor preço à vista (no KaBuM!, o PIX); usado para decidir o aviso. */
  precoPix: number
  disponivel: boolean
}

export type ResultadoAviso = 'ENVIADO' | 'EMAIL_NAO_CONFIGURADO' | 'FALHOU'

export interface Monitoramento {
  id: number
  loja: LojaId
  lojaNome: string
  codigoProduto: string
  nome: string
  link: string
  imagem: string | null
  precoMaximo: number
  precoAtual: number | null
  precoPixAtual: number | null
  disponivel: boolean
  precoNotificado: number | null
  ultimoAvisoResultado: ResultadoAviso | null
  ultimoAvisoEm: string | null
  criadoEm: string
  ultimaVerificacao: string | null
}

export interface NovoMonitoramento {
  loja: LojaId
  codigoProduto: string
  precoMaximo: number
}
