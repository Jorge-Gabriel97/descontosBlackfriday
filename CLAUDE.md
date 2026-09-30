# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Monitor de preços: o usuário escolhe um produto exato numa loja online, define o preço máximo e recebe um
e-mail quando o preço à vista (no KaBuM!, o PIX) chega lá. Código, mensagens de erro, commits e conversa
com o Jorge (dono do projeto) são em português.

## Comandos

Backend (Spring Boot 4.1, Java 17, Maven Wrapper; no PowerShell use `.\mvnw.cmd`):

```bash
cd backend
./mvnw spring-boot:run                                         # API em http://localhost:8080
./mvnw test                                                    # todos os testes
./mvnw test -Dtest=KabumClienteTest                            # uma classe
./mvnw test -Dtest=KabumClienteTest#slugRemoveAcentosESimbolos # um método
```

Frontend (Vite + React 19 + TypeScript, lint com oxlint):

```bash
cd frontend
npm run dev     # http://localhost:5173, repassa /api para o backend (vite.config.ts)
npm run build   # tsc -b + vite build; é a checagem de tipos
npm run lint
```

O frontend não tem testes automatizados (issue #4).

Antes do primeiro commit em um clone: `git config core.hooksPath .githooks`. O hook `.githooks/pre-commit`
bloqueia segredos (chaves da Brevo, tokens, `PASSWORD = "literal"`, arquivos `.env`/`.pem`). Um alerta do
GitGuardian já foi disparado por exemplos de credencial no README; na documentação, use tabela ou
marcador, nunca `VAR = "valor"`.

## Arquitetura

**Lojas plugáveis.** `loja/Loja` é o enum de todas as lojas conhecidas; uma loja só fica "ativa" quando
existe um `@Component` que implementa `loja/LojaCliente` (`buscar(termo)` e `consultar(codigo)`).
`loja/Lojas` monta o mapa a partir dos beans injetados. Hoje só existe `kabum/KabumCliente`. As demais lojas
bloqueiam scraping e só devem entrar pelas APIs oficiais ou de afiliados (issues #10–#13).

**KabumCliente lê o JSON de `<script id="__NEXT_DATA__">`** das páginas públicas. Na busca/categoria,
`props.pageProps.data` vem como *texto* JSON dentro do JSON, e os produtos ficam em `catalogServer.data`;
na página de produto, em `props.pageProps.product`. Regras de respeito ao site que o código garante e que
não devem ser relaxadas:
- só `/busca/<slug>` e `/produto/<codigo>` (código só com dígitos), nunca com parâmetros de URL;
- redirecionamentos são seguidos à mão (`HttpClient.Redirect.NEVER`): só dentro de `www.kabum.com.br`,
  sem a query e fora dos caminhos bloqueados no robots.txt (`caminhoPermitido`). Busca e produto costumam
  redirecionar (308 para a categoria, 301 para a URL com nome);
- User-Agent próprio e `app.kabum.intervalo-entre-requisicoes` (5 s) entre **todas** as requisições,
  inclusive entre os passos de um redirecionamento. Por isso `baixar` é `synchronized`.

Falha do site na busca vira `ResponseStatusException` 503 com mensagem explicativa; em `consultar`, vira
`Optional.empty()` e a verificação agendada tenta de novo no próximo ciclo.

**Ciclo de monitoramento.** `MonitoramentoService.verificarTodos` roda em `@Scheduled`
(`app.monitor.intervalo` = PT1H, primeira execução 1 min após subir), agrupa os monitoramentos por
(loja, código) para consultar cada produto uma vez só e aplica o preço em cada um. A regra de quando avisar
mora na entidade `ProdutoMonitorado`: `registrarPreco` decide (preço PIX ≤ máximo; depois de um aviso
entregue, só avisa de novo se cair abaixo de `precoNotificado`; voltar acima do máximo zera o ciclo) e
`registrarAviso` só marca como avisado quando o resultado é `ENVIADO`, para uma falha do SMTP ser tentada
de novo. Produto indisponível não gera aviso.

**E-mail.** `NotificadorEmail` recebe `ObjectProvider<JavaMailSender>`: sem `SPRING_MAIL_HOST` no ambiente
não existe sender, e o aviso só vai para o log (`EMAIL_NAO_CONFIGURADO`). As credenciais ficam só em
variáveis de ambiente do usuário Windows (`backend/configurar-email.ps1`). Em falhas, `descreverFalha`
percorre a cadeia de causas, porque o Spring esconde a resposta SMTP real (ex.: `525 5.7.1 Unauthorized IP
address` da Brevo) atrás de "Authentication failed".

**Testes não podem usar o SMTP real.** Como as variáveis `SPRING_MAIL_*` estão no Windows, os
`@SpringBootTest` enviariam e-mails de verdade. `src/test/resources/config/application.properties` exclui
`MailSenderAutoConfiguration` (pacote `org.springframework.boot.mail.autoconfigure` no Boot 4). Os testes
de integração usam H2 em memória (`spring.datasource.url` no `@SpringBootTest`); o app usa H2 em arquivo em
`backend/data/`. Testes que acessam HTTP sobem um servidor local (`com.sun.net.httpserver.HttpServer`,
SMTP falso em `ServerSocket`) em vez de acessar o KaBuM! ou a Brevo. `KabumCliente` tem um construtor de
pacote que recebe o endereço base para isso.

**Segurança.** Sessão com cookie HttpOnly (7 dias) e CSRF no modo SPA (`SegurancaConfig`): o backend grava
`XSRF-TOKEN` e o frontend (`api.ts`) devolve em `X-XSRF-TOKEN` em todo método que não é GET. Fora
`/api/status`, cadastro e login, tudo em `/api/**` exige login, e o resto é `denyAll`. Cada usuário só vê e
altera os próprios monitoramentos. E-mails são normalizados com `Usuario.normalizarEmail`.

**Erros da API** saem sempre como `{status, message}` em português (`config/TratadorDeErros`, com nomes de
campo traduzidos em `CAMPOS`). O frontend mostra `message`; só usa textos padrão quando a resposta vem
vazia (502/503/504 sem corpo = proxy do Vite avisando que o backend está desligado).

Bibliotecas com pacote novo: Jackson 3 (`tools.jackson.databind`) e os módulos do Spring Boot 4
(`org.springframework.boot.<modulo>.autoconfigure`, `spring-boot-starter-webmvc`).

## Como o Jorge trabalha

- Por etapas: implementar, testar e resumir para ele validar; só depois fazer o commit (um por etapa,
  mensagem em português, com `Closes #N` quando fecha uma issue) e o push.
- As pendências são as issues do GitHub (`Jorge-Gabriel97/descontosBlackfriday`, repositório público).
  O `gh` não está instalado; leia as issues pela API REST pública (ex.: `Invoke-RestMethod` no PowerShell).
- Para acessar o KaBuM! em investigações, use o User-Agent do app e espere 5 s entre as requisições.
