import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { criarConta, entrar, esqueciSenha } from '../api'
import type { Usuario } from '../types'
import { TelaAcesso } from './TelaAcesso'

vi.mock('../api', () => ({ entrar: vi.fn(), criarConta: vi.fn(), esqueciSenha: vi.fn() }))

const usuario: Usuario = { id: 1, nome: 'Jorge', email: 'jorge@exemplo.com', emailConfirmado: true }

async function preencherLogin(user: ReturnType<typeof userEvent.setup>, email: string, senha: string) {
  await user.type(screen.getByLabelText('E-mail'), email)
  await user.type(screen.getByLabelText(/^Senha/), senha)
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
}

describe('TelaAcesso', () => {
  beforeEach(() => vi.resetAllMocks())

  it('entra com o e-mail sem espaços e avisa quem chamou', async () => {
    vi.mocked(entrar).mockResolvedValue(usuario)
    const onEntrou = vi.fn()
    const user = userEvent.setup()
    render(<TelaAcesso onEntrou={onEntrou} />)

    await preencherLogin(user, '  jorge@exemplo.com ', 'segredo123')

    expect(entrar).toHaveBeenCalledWith('jorge@exemplo.com', 'segredo123')
    expect(onEntrou).toHaveBeenCalledWith(usuario)
  })

  it('mostra o erro da API e libera o botão de novo', async () => {
    vi.mocked(entrar).mockRejectedValue(new Error('E-mail ou senha incorretos.'))
    const onEntrou = vi.fn()
    const user = userEvent.setup()
    render(<TelaAcesso onEntrou={onEntrou} />)

    await preencherLogin(user, 'jorge@exemplo.com', 'errada123')

    expect(await screen.findByText('E-mail ou senha incorretos.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled()
    expect(onEntrou).not.toHaveBeenCalled()
  })

  it('cria a conta com nome, e-mail e senha', async () => {
    vi.mocked(criarConta).mockResolvedValue(usuario)
    const onEntrou = vi.fn()
    const user = userEvent.setup()
    render(<TelaAcesso onEntrou={onEntrou} />)

    await user.click(screen.getByRole('button', { name: 'Criar conta' }))
    expect(screen.getByRole('heading', { name: 'Criar conta' })).toBeInTheDocument()
    expect(screen.getByLabelText(/^Senha/)).toHaveAttribute('minLength', '8')

    await user.type(screen.getByLabelText('Nome'), ' Jorge ')
    await user.type(screen.getByLabelText('E-mail'), 'jorge@exemplo.com')
    await user.type(screen.getByLabelText(/^Senha/), 'segredo123')
    await user.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(criarConta).toHaveBeenCalledWith('Jorge', 'jorge@exemplo.com', 'segredo123')
    expect(entrar).not.toHaveBeenCalled()
    expect(onEntrou).toHaveBeenCalledWith(usuario)
  })

  it('pede o link de nova senha e confirma o envio', async () => {
    vi.mocked(esqueciSenha).mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<TelaAcesso onEntrou={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: 'Esqueci minha senha' }))
    expect(screen.queryByLabelText(/^Senha/)).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('E-mail'), 'jorge@exemplo.com')
    await user.click(screen.getByRole('button', { name: 'Enviar link' }))

    expect(esqueciSenha).toHaveBeenCalledWith('jorge@exemplo.com')
    expect(await screen.findByText(/enviamos um link para criar uma nova senha/)).toBeInTheDocument()
  })

  it('trocar de modo apaga o erro anterior', async () => {
    vi.mocked(entrar).mockRejectedValue(new Error('E-mail ou senha incorretos.'))
    const user = userEvent.setup()
    render(<TelaAcesso onEntrou={vi.fn()} />)

    await preencherLogin(user, 'jorge@exemplo.com', 'errada123')
    await screen.findByText('E-mail ou senha incorretos.')
    await user.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(screen.queryByText('E-mail ou senha incorretos.')).not.toBeInTheDocument()
  })
})
