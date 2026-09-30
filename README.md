# Descontos Black Friday

Monitor de preços em lojas online. O usuário cria uma conta, busca um produto, seleciona
o item exato e define o preço máximo que aceita pagar. O app verifica o preço a cada hora
e envia um aviso **para o e-mail da conta** quando o preço à vista chega ao valor definido.

Lojas: **KaBuM!** (ativa). Shopee, Mercado Livre, Amazon e AliExpress aparecem como "em breve"
(veja [Adicionar uma loja](#adicionar-uma-loja)).

| Parte | Tecnologia | Pasta |
|---|---|---|
| Backend (API, login, verificação agendada, e-mail) | Java 17, Spring Boot 4, Spring Security, H2 | `backend/` |
| Frontend | Node.js, React, TypeScript, Vite | `frontend/` |

## Como rodar

Pré-requisitos: Java 17+ e Node.js 20+. O Maven não precisa estar instalado (o projeto usa o `mvnw`).

```bash
# Terminal 1 — backend em http://localhost:8080
cd backend
./mvnw spring-boot:run        # no PowerShell/cmd: .\mvnw.cmd spring-boot:run

# Terminal 2 — frontend em http://localhost:5173
cd frontend
npm install
npm run dev
```

Abra <http://localhost:5173>, clique em **Criar conta** e comece a monitorar.

O frontend repassa as chamadas `/api` para o backend (configurado em `frontend/vite.config.ts`),
então os dois ficam na mesma origem e o cookie de login funciona sem CORS.
Contas e monitoramentos ficam salvos em `backend/data/` (banco H2 em arquivo).

**Antes do primeiro commit**, ative a verificação de segredos (uma vez por clone):

```bash
git config core.hooksPath .githooks
```

Ela bloqueia commits com chaves, senhas ou tokens escritos no código e com arquivos como `.env` ou `.pem`.

## Login e segurança

- Conta com nome, e-mail e senha (mínimo de 8 caracteres). A senha é guardada com **BCrypt**, nunca em texto.
- O login usa **sessão com cookie `HttpOnly`** (o JavaScript da página não consegue lê-lo). A sessão expira após 7 dias sem uso.
- **Proteção CSRF**: o backend entrega um token no cookie `XSRF-TOKEN` e o frontend o devolve no
  cabeçalho `X-XSRF-TOKEN` em toda requisição que altera dados.
- No login, o id da sessão e o token CSRF são trocados (evita fixação de sessão).
- Cada usuário só vê e altera os próprios monitoramentos; a API responde 404 para os de outra conta.
- O console web do H2 fica desligado.

Ainda não implementado (sugestões para próximas etapas): recuperação de senha por e-mail,
confirmação do e-mail no cadastro e limite de tentativas de login.

## Configurar o envio de e-mail

Sem configuração, o app funciona normalmente, mas os avisos **não chegam à caixa de entrada**:
a tela mostra uma faixa de alerta e o card indica "e-mail não enviado". Assim que o e-mail for
configurado, o aviso pendente é enviado na próxima verificação.

As credenciais vão em variáveis de ambiente. Nunca coloque a senha no código ou no Git.
O `SPRING_MAIL_USERNAME` é apenas o **remetente**; o aviso vai para o e-mail da conta de cada usuário.
A configuração é feita uma única vez, por quem administra o app; quem usa o app só cria a conta.

**Jeito mais fácil (Windows):** rode o script abaixo, que pergunta cada valor (a senha fica escondida)
e salva tudo nas variáveis do seu usuário do Windows. Depois, reinicie o backend.

```powershell
powershell -ExecutionPolicy Bypass -File "backend\configurar-email.ps1"
```

### Valores de cada provedor

| Variável | Gmail | Brevo (plano gratuito) |
|---|---|---|
| `SPRING_MAIL_HOST` | `smtp.gmail.com` | `smtp-relay.brevo.com` |
| `SPRING_MAIL_PORT` | `587` | `587` |
| `SPRING_MAIL_USERNAME` | seu e-mail do Gmail | login SMTP mostrado pela Brevo |
| `SPRING_MAIL_PASSWORD` | senha de app (16 letras) | chave SMTP |
| `APP_MAIL_REMETENTE` | (não precisa) | e-mail confirmado em **Senders** |

**Gmail:** ative a verificação em duas etapas e crie uma **senha de app** em
<https://myaccount.google.com/apppasswords>. Se a página disser que a opção não está disponível,
a conta não tem a verificação em duas etapas ativa, ou é uma conta de trabalho/escola que bloqueia
senhas de app. Nesse caso, use a Brevo.

**Brevo:** crie uma conta em <https://www.brevo.com>, confirme o e-mail remetente e, em
**SMTP & API → SMTP**, gere uma **chave SMTP** (começa com `xsmtpsib-`; não use a da aba API keys,
que começa com `xkeysib-`).

Para testar só numa janela do PowerShell, sem salvar nada, defina as variáveis com `$env:NOME = ...`.
Para a senha/chave, use o comando abaixo, que pede o valor escondido em vez de escrevê-lo no terminal:

```powershell
$env:SPRING_MAIL_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Chave SMTP' -AsSecureString)).Password
```

### Problemas comuns

- **`Authentication failed` no log com a chave certa:** a Brevo bloqueia IPs desconhecidos
  (resposta `525 5.7.1 Unauthorized IP address`). Autorize o IP pelo e-mail de alerta da Brevo
  ou em **Security → Authorized IPs**. Internet residencial troca de IP; para testes, desative o bloqueio.
- **E-mail não chega:** confira o spam. Remetente `@gmail.com` enviado pela Brevo tende a cair no spam;
  em produção, use um domínio próprio autenticado na Brevo.
- O log do backend registra cada verificação (`Monitoramento N (...) -> enviando aviso / sem aviso`)
  e cada envio (`Aviso do produto ... enviado` ou `Falha ao enviar e-mail`).

## Regras de aviso

- O aviso sai quando o **preço à vista** (no KaBuM!, o preço no PIX) fica igual ou abaixo do preço máximo.
- Depois de um aviso entregue, só sai outro se o preço cair ainda mais.
- Se o preço voltar a subir acima do máximo, a próxima queda gera um novo aviso.
- Se o e-mail não sair (servidor sem e-mail configurado ou falha do SMTP), a próxima verificação tenta de novo.
- Produto indisponível não gera aviso.

O card de cada produto mostra em que pé está: quanto falta para o seu preço, se o aviso foi
enviado (e quando), ou por que não foi.

## Uso do site do KaBuM!

O app lê páginas públicas do KaBuM! e foi feito para respeitar o site:

- **robots.txt**: acessa apenas `/busca/<termo>` (sem parâmetros de URL) e `/produto/<código>`, caminhos permitidos.
- **Identificação**: usa o User-Agent `DescontosBlackfriday/0.1`, sem se passar por navegador.
- **Ritmo**: espera 5 segundos entre requisições e verifica cada produto uma vez por hora,
  mesmo que vários usuários monitorem o mesmo item.
- **Políticas do Site (tópico "Oferta")**: só valem os preços da página do produto e só são
  e-mails do KaBuM! os de domínio `@kabum.com.br`. Por isso o e-mail do app informa a data e hora
  da leitura, manda conferir o preço no link e diz que o app não tem vínculo com a loja.

Se o KaBuM! mudar o layout das páginas, a leitura pode parar de funcionar; os erros aparecem no log.

Os ajustes ficam em `backend/src/main/resources/application.properties`
(`app.monitor.intervalo`, `app.kabum.intervalo-entre-requisicoes`, `app.kabum.user-agent`).

## Adicionar uma loja

1. Crie uma classe `@Component` que implemente `loja/LojaCliente` (veja `kabum/KabumCliente` como exemplo),
   retornando a loja correspondente do enum `loja/Loja` em `loja()`.
2. Pronto: a loja passa a aparecer como ativa na tela, e busca, monitoramento, verificação
   agendada e e-mail funcionam sem outras mudanças.

Antes de implementar, verifique os termos de uso e o `robots.txt` de cada loja. Mercado Livre e
Amazon bloqueiam leitura automatizada das páginas; o caminho indicado para elas (e para Shopee e
AliExpress) são as APIs oficiais ou de afiliados, que exigem cadastro e credenciais.

## API

Todas as rotas, exceto `status`, `auth/cadastro` e `auth/login`, exigem login.
Requisições que alteram dados precisam do cabeçalho `X-XSRF-TOKEN`.

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/status` | Se o e-mail está configurado e quais lojas estão ativas |
| POST | `/api/auth/cadastro` | Cria conta `{nome, email, senha}` e já entra |
| POST | `/api/auth/login` | Entra `{email, senha}` |
| POST | `/api/auth/logout` | Sai |
| GET | `/api/auth/eu` | Usuário logado |
| GET | `/api/lojas/{loja}/busca?termo=...` | Busca produtos em uma loja (ex.: `KABUM`) |
| GET | `/api/monitoramentos` | Lista os monitoramentos do usuário |
| POST | `/api/monitoramentos` | Cadastra `{loja, codigoProduto, precoMaximo}` |
| POST | `/api/monitoramentos/{id}/verificar` | Consulta o preço agora |
| DELETE | `/api/monitoramentos/{id}` | Para de monitorar |

Erros voltam como `{status, message}`, com a mensagem em português.

## Testes

```bash
cd backend && ./mvnw test
cd frontend && npm run build && npm run lint
```
