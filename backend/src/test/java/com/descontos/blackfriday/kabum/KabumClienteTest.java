package com.descontos.blackfriday.kabum;

import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.loja.ProdutoLoja;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KabumClienteTest {

    private final KabumCliente cliente = new KabumCliente("teste", Duration.ZERO);

    private static String pagina(String json) {
        return "<html><body><script id=\"__NEXT_DATA__\" type=\"application/json\">" + json + "</script></body></html>";
    }

    @Test
    void extraiResultadosDaBusca() {
        String html = pagina("""
                {"props":{"pageProps":{"data":{"catalogServer":{"data":[
                  {"code":541147,"name":"Cafeteira Mondial","friendlyName":"cafeteira-mondial",
                   "image":"https://img/1.jpg","price":249.9,"priceWithDiscount":237.41,"available":true}
                ]}}}}}
                """);

        List<ProdutoLoja> produtos = cliente.extrairBusca(html);

        assertThat(produtos).containsExactly(new ProdutoLoja(Loja.KABUM, "541147", "Cafeteira Mondial",
                "https://www.kabum.com.br/produto/541147/cafeteira-mondial", "https://img/1.jpg",
                new BigDecimal("249.9"), new BigDecimal("237.41"), true));
    }

    @Test
    void extraiBuscaQuandoDataVemComoTexto() {
        String html = pagina("""
                {"props":{"pageProps":{"data":"{\\"catalogServer\\":{\\"data\\":[{\\"code\\":1,\\"name\\":\\"X\\",\\"friendlyName\\":\\"x\\",\\"price\\":10,\\"priceWithDiscount\\":9,\\"available\\":true}]}}"}}}
                """);

        assertThat(cliente.extrairBusca(html)).extracting(ProdutoLoja::codigo).containsExactly("1");
    }

    @Test
    void extraiPaginaDoProduto() {
        String html = pagina("""
                {"props":{"pageProps":{"product":{"id":541147,"title":"Cafeteira Mondial",
                  "friendlyName":"cafeteira-mondial","thumbnail":"https://img/t.jpg","available":true,
                  "prices":{"price":249.9,"priceWithDiscount":237.41,"oldPrice":0}}}}}
                """);

        Optional<ProdutoLoja> produto = cliente.extrairProduto(html);

        assertThat(produto).hasValueSatisfying(p -> {
            assertThat(p.codigo()).isEqualTo("541147");
            assertThat(p.nome()).isEqualTo("Cafeteira Mondial");
            assertThat(p.preco()).isEqualByComparingTo("249.90");
            assertThat(p.precoPix()).isEqualByComparingTo("237.41");
            assertThat(p.disponivel()).isTrue();
        });
    }

    @Test
    void paginaSemDadosNaoGeraProduto() {
        assertThat(cliente.extrairProduto("<html></html>")).isEmpty();
        assertThat(cliente.extrairBusca("<html></html>")).isEmpty();
    }

    @Test
    void recusaCodigoQueNaoSejaNumerico() {
        assertThat(cliente.consultar("../minha-conta")).isEmpty();
        assertThat(cliente.consultar("123?x=1")).isEmpty();
    }

    @Test
    void extraiProdutosDaPaginaDeCategoria() throws Exception {
        List<ProdutoLoja> produtos = cliente.extrairBusca(categoriaNotebooks());

        assertThat(produtos).extracting(ProdutoLoja::codigo).containsExactly("1037468", "1066088");
        assertThat(produtos.get(0).nome()).startsWith("Notebook Lenovo IdeaPad Slim 3");
        assertThat(produtos.get(0).precoPix()).isEqualByComparingTo("4703.04");
    }

    @Test
    void buscaSegueORedirecionamentoParaACategoriaSemParametros() throws Exception {
        List<String> acessos = new ArrayList<>();
        HttpServer site = kabumFalso(acessos);
        try {
            List<ProdutoLoja> produtos = clienteLocal(site).buscar("notebook");

            assertThat(produtos).hasSize(2);
            assertThat(acessos).containsExactly("/busca/notebook", "/computadores/notebooks");
        } finally {
            site.stop(0);
        }
    }

    @Test
    void consultaSegueORedirecionamentoDoProduto() throws Exception {
        List<String> acessos = new ArrayList<>();
        HttpServer site = kabumFalso(acessos);
        try {
            Optional<ProdutoLoja> produto = clienteLocal(site).consultar("541147");

            assertThat(produto).map(ProdutoLoja::codigo).hasValue("541147");
            assertThat(acessos).containsExactly("/produto/541147", "/produto/541147/cafeteira-mondial");
        } finally {
            site.stop(0);
        }
    }

    @Test
    void buscaExplicaQuandoOSiteFalhaEmVezDeVoltarVazia() throws Exception {
        List<String> acessos = new ArrayList<>();
        HttpServer site = kabumFalso(acessos);
        try {
            KabumCliente local = clienteLocal(site);

            assertThatThrownBy(() -> local.buscar("fora do ar"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining(KabumCliente.BUSCA_FALHOU);
            assertThatThrownBy(() -> local.buscar("pede login"))
                    .isInstanceOf(ResponseStatusException.class);
            assertThat(acessos).containsExactly("/busca/fora-do-ar", "/busca/pede-login");
        } finally {
            site.stop(0);
        }
    }

    @Test
    void redirecionamentoSoDentroDoSiteESemParametros() {
        assertThat(KabumCliente.caminhoPermitido("/computadores/notebooks?search-term=notebook"))
                .hasValue("/computadores/notebooks");
        assertThat(KabumCliente.caminhoPermitido("https://www.kabum.com.br/produto/1/x#avaliacoes"))
                .hasValue("/produto/1/x");
        assertThat(KabumCliente.caminhoPermitido("https://golpe.example.com/produto/1")).isEmpty();
        assertThat(KabumCliente.caminhoPermitido("http://www.kabum.com.br/produto/1")).isEmpty();
        assertThat(KabumCliente.caminhoPermitido("/minha-conta/pedidos")).isEmpty();
        assertThat(KabumCliente.caminhoPermitido("")).isEmpty();
    }

    private static String categoriaNotebooks() throws IOException {
        try (InputStream in = KabumClienteTest.class.getResourceAsStream("/kabum/categoria-notebooks.html")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static KabumCliente clienteLocal(HttpServer site) {
        return new KabumCliente("teste", Duration.ZERO, "http://localhost:" + site.getAddress().getPort());
    }

    private static HttpServer kabumFalso(List<String> acessos) throws IOException {
        String categoria = categoriaNotebooks();
        String produto = pagina("""
                {"props":{"pageProps":{"product":{"id":541147,"title":"Cafeteira Mondial",
                  "friendlyName":"cafeteira-mondial","available":true,
                  "prices":{"price":249.9,"priceWithDiscount":237.41}}}}}
                """);
        HttpServer site = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        site.createContext("/", troca -> {
            acessos.add(troca.getRequestURI().toString());
            switch (troca.getRequestURI().toString()) {
                case "/busca/notebook" -> redirecionar(troca, 308, "/computadores/notebooks?search-term=notebook");
                case "/computadores/notebooks" -> responder(troca, 200, categoria);
                case "/produto/541147" -> redirecionar(troca, 301, "https://www.kabum.com.br/produto/541147/cafeteira-mondial");
                case "/produto/541147/cafeteira-mondial" -> responder(troca, 200, produto);
                case "/busca/pede-login" -> redirecionar(troca, 302, "/login");
                default -> responder(troca, 503, "fora do ar");
            }
        });
        site.start();
        return site;
    }

    private static void redirecionar(HttpExchange troca, int status, String destino) throws IOException {
        troca.getResponseHeaders().add("Location", destino);
        troca.sendResponseHeaders(status, -1);
        troca.close();
    }

    private static void responder(HttpExchange troca, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = troca.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void slugRemoveAcentosESimbolos() {
        assertThat(KabumCliente.slug("  Fone Bluetooth, Ação! ")).isEqualTo("fone-bluetooth-acao");
    }
}
