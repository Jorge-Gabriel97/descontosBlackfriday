import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { formatarDataHora } from '../formatar'
import type { Monitoramento } from '../types'
import { MonitoramentoCard } from './MonitoramentoCard'

const EMAIL = 'jorge@exemplo.com'

// Por padrão o preço PIX (900) já está abaixo do máximo (1000)
function monitoramento(campos: Partial<Monitoramento> = {}): Monitoramento {
  return {
    id: 1,
    loja: 'KABUM',
    lojaNome: 'KaBuM!',
    codigoProduto: '123',
    nome: 'Placa de vídeo',
    link: 'https://www.kabum.com.br/produto/123',
    imagem: null,
    precoMaximo: 1000,
    precoAtual: 1100,
    precoPixAtual: 900,
    disponivel: true,
    precoNotificado: null,
    ultimoAvisoResultado: null,
    ultimoAvisoEm: null,
    criadoEm: '2026-10-01T10:00:00',
    ultimaVerificacao: '2026-10-05T09:00:00',
    ...campos,
  }
}

function renderizar(m: Monitoramento, emailConfigurado = true, onVerificar = vi.fn().mockResolvedValue(undefined)) {
  render(
    <MonitoramentoCard
      monitoramento={m}
      emailConta={EMAIL}
      emailConfigurado={emailConfigurado}
      onVerificar={onVerificar}
      onRemover={vi.fn().mockResolvedValue(undefined)}
    />,
  )
}

describe('situação do card', () => {
  it('sem preço ainda: aguardando a primeira verificação', () => {
    renderizar(monitoramento({ precoPixAtual: null, precoAtual: null, ultimaVerificacao: null }))

    expect(screen.getByText('Aguardando a primeira verificação')).toBeInTheDocument()
    expect(screen.getByText('Verificado em nunca')).toBeInTheDocument()
  })

  it('indisponível vem antes de qualquer comparação de preço', () => {
    renderizar(monitoramento({ disponivel: false, ultimoAvisoResultado: 'ENVIADO' }))

    expect(screen.getByText('Produto indisponível na loja')).toBeInTheDocument()
  })

  it('acima do preço: mostra quanto falta e a barra de progresso', () => {
    renderizar(monitoramento({ precoPixAtual: 1250 }))

    expect(screen.getByText('Aguardando: faltam R$ 250,00 (20,0%)')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '80')
  })

  it('preço atingido e aviso enviado', () => {
    const em = '2026-10-05T09:00:00'
    renderizar(monitoramento({ ultimoAvisoResultado: 'ENVIADO', ultimoAvisoEm: em }))

    expect(screen.getByText('Preço atingido · aviso enviado')).toBeInTheDocument()
    expect(screen.getByText(`E-mail enviado para ${EMAIL} em ${formatarDataHora(em)}.`)).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('preço igual ao máximo também conta como atingido', () => {
    renderizar(monitoramento({ precoPixAtual: 1000, ultimoAvisoResultado: 'ENVIADO' }))

    expect(screen.getByText('Preço atingido · aviso enviado')).toBeInTheDocument()
  })

  it('preço atingido com o e-mail da conta não confirmado', () => {
    renderizar(monitoramento({ ultimoAvisoResultado: 'EMAIL_NAO_CONFIRMADO' }))

    expect(screen.getByText('Preço atingido · confirme seu e-mail')).toBeInTheDocument()
    expect(screen.getByText(/assim que você confirmar jorge@exemplo\.com/)).toBeInTheDocument()
  })

  it('preço atingido e o envio falhou', () => {
    renderizar(monitoramento({ ultimoAvisoResultado: 'FALHOU' }))

    expect(screen.getByText('Preço atingido · o e-mail falhou')).toBeInTheDocument()
  })

  it('e-mail não enviado com o servidor de e-mail configurado', () => {
    renderizar(monitoramento({ ultimoAvisoResultado: 'EMAIL_NAO_CONFIGURADO' }), true)

    expect(screen.getByText('Preço atingido · e-mail não enviado')).toBeInTheDocument()
    expect(screen.getByText('O envio será tentado de novo na próxima verificação.')).toBeInTheDocument()
  })

  it('e-mail não enviado porque o servidor não tem e-mail configurado', () => {
    renderizar(monitoramento({ ultimoAvisoResultado: null }), false)

    expect(screen.getByText('Preço atingido · e-mail não enviado')).toBeInTheDocument()
    expect(screen.getByText(/não está configurado no servidor/)).toBeInTheDocument()
  })
})

describe('ações do card', () => {
  it('mostra o erro de "Verificar agora" e libera os botões', async () => {
    const onVerificar = vi.fn().mockRejectedValue(new Error('Não foi possível consultar a loja.'))
    const user = userEvent.setup()
    renderizar(monitoramento(), true, onVerificar)

    await user.click(screen.getByRole('button', { name: 'Verificar agora' }))

    expect(onVerificar).toHaveBeenCalledWith(1)
    expect(await screen.findByText('Não foi possível consultar a loja.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remover' })).toBeEnabled()
  })
})
