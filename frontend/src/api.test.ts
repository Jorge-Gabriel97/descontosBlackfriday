import { afterEach, describe, expect, it, vi } from 'vitest'
import { entrar, ErroApi, listarMonitoramentos, removerMonitoramento } from './api'

const SERVIDOR_FORA = 'Não foi possível conectar ao servidor. O backend está rodando na porta 8080?'

function responder(status: number, corpo?: unknown) {
  const fetch = vi.fn().mockResolvedValue(new Response(corpo === undefined ? null : JSON.stringify(corpo), { status }))
  vi.stubGlobal('fetch', fetch)
  return fetch
}

async function erroDe(promessa: Promise<unknown>): Promise<ErroApi> {
  const erro = await promessa.catch((e: unknown) => e)
  expect(erro).toBeInstanceOf(ErroApi)
  return erro as ErroApi
}

describe('mensagens de erro da API', () => {
  it('mostra a mensagem que o backend mandou', async () => {
    responder(400, { status: 400, message: 'E-mail ou senha incorretos.' })

    const erro = await erroDe(entrar('a@b.com', 'x'))

    expect(erro.status).toBe(400)
    expect(erro.message).toBe('E-mail ou senha incorretos.')
  })

  it.each([
    [401, 'Sua sessão expirou. Entre novamente.'],
    [403, 'Sessão inválida. Recarregue a página e tente de novo.'],
    [404, 'Não encontrado.'],
    [502, SERVIDOR_FORA],
    [503, SERVIDOR_FORA],
    [504, SERVIDOR_FORA],
    [500, 'Erro 500'],
  ])('resposta %i sem corpo usa o texto padrão', async (status, mensagem) => {
    responder(status)

    expect((await erroDe(listarMonitoramentos())).message).toBe(mensagem)
  })

  it('corpo sem message também usa o texto padrão', async () => {
    responder(401, { status: 401 })

    expect((await erroDe(listarMonitoramentos())).message).toBe('Sua sessão expirou. Entre novamente.')
  })

  it('falha de rede vira status 0 com aviso de servidor fora', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const erro = await erroDe(listarMonitoramentos())

    expect(erro.status).toBe(0)
    expect(erro.message).toBe(SERVIDOR_FORA)
  })
})

describe('requisições', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('devolve o token CSRF só nos métodos que não são GET', async () => {
    document.cookie = 'XSRF-TOKEN=abc%3D1'
    const get = responder(200, [])
    await listarMonitoramentos()
    const del = responder(204)
    await removerMonitoramento(7)

    expect(new Headers(get.mock.calls[0][1].headers).has('X-XSRF-TOKEN')).toBe(false)
    expect(new Headers(del.mock.calls[0][1].headers).get('X-XSRF-TOKEN')).toBe('abc=1')
  })

  it('resposta 204 resolve sem corpo', async () => {
    responder(204)

    await expect(removerMonitoramento(1)).resolves.toBeUndefined()
  })
})
