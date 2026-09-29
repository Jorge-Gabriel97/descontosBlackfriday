package com.descontos.blackfriday.kabum;

import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.loja.ProdutoLoja;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
        // Não chega a acessar o site: o código iria para a URL
        assertThat(cliente.consultar("../minha-conta")).isEmpty();
        assertThat(cliente.consultar("123?x=1")).isEmpty();
    }

    @Test
    void slugRemoveAcentosESimbolos() {
        assertThat(KabumCliente.slug("  Fone Bluetooth, Ação! ")).isEqualTo("fone-bluetooth-acao");
    }
}
