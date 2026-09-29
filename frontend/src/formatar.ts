const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const dataHora = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

export const formatarPreco = (valor: number | null) => (valor == null ? '—' : moeda.format(valor))

export const formatarDataHora = (iso: string | null) => (iso ? dataHora.format(new Date(iso)) : 'nunca')
