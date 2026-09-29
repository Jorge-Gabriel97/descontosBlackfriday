package com.descontos.blackfriday.kabum;

import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.loja.LojaCliente;
import com.descontos.blackfriday.loja.ProdutoLoja;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê as páginas públicas do KaBuM!, que trazem os dados do produto como JSON
 * na tag {@code __NEXT_DATA__}.
 *
 * <p>Regras seguidas para respeitar o site (robots.txt e Políticas do KaBuM!):
 * <ul>
 *   <li>só acessa {@code /busca/<termo>} sem parâmetros de URL e {@code /produto/<codigo>},
 *       caminhos liberados no robots.txt;</li>
 *   <li>se identifica com um User-Agent próprio, sem se passar por navegador;</li>
 *   <li>espera um intervalo mínimo entre uma requisição e outra.</li>
 * </ul>
 */
@Component
public class KabumCliente implements LojaCliente {

    private static final Logger log = LoggerFactory.getLogger(KabumCliente.class);
    private static final String BASE = "https://www.kabum.com.br";
    private static final Pattern NEXT_DATA =
            Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);
    private static final Pattern CODIGO_VALIDO = Pattern.compile("\\d{1,12}");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String userAgent;
    private final long intervaloMillis;
    private long ultimaRequisicao;

    public KabumCliente(@Value("${app.kabum.user-agent}") String userAgent,
                        @Value("${app.kabum.intervalo-entre-requisicoes}") Duration intervalo) {
        this.userAgent = userAgent;
        this.intervaloMillis = intervalo.toMillis();
    }

    @Override
    public Loja loja() {
        return Loja.KABUM;
    }

    @Override
    public List<ProdutoLoja> buscar(String termo) {
        String slug = slug(termo);
        if (slug.isEmpty()) {
            return List.of();
        }
        return baixar(BASE + "/busca/" + slug).map(this::extrairBusca).orElse(List.of());
    }

    @Override
    public Optional<ProdutoLoja> consultar(String codigo) {
        // O código vai para a URL: aceita só dígitos para não montar caminhos arbitrários
        if (codigo == null || !CODIGO_VALIDO.matcher(codigo).matches()) {
            return Optional.empty();
        }
        return baixar(BASE + "/produto/" + codigo).flatMap(this::extrairProduto);
    }

    List<ProdutoLoja> extrairBusca(String html) {
        List<ProdutoLoja> produtos = new ArrayList<>();
        for (JsonNode item : pageProps(html).path("data").path("catalogServer").path("data")) {
            String codigo = item.path("code").asString();
            produtos.add(new ProdutoLoja(
                    Loja.KABUM,
                    codigo,
                    item.path("name").asString(),
                    link(codigo, item.path("friendlyName").asString()),
                    item.path("image").asString(),
                    item.path("price").decimalValue(),
                    item.path("priceWithDiscount").decimalValue(),
                    item.path("available").asBoolean(false)));
        }
        return produtos;
    }

    Optional<ProdutoLoja> extrairProduto(String html) {
        JsonNode p = pageProps(html).path("product");
        if (p.isMissingNode() || !p.has("id")) {
            return Optional.empty();
        }
        String codigo = p.path("id").asString();
        JsonNode precos = p.path("prices");
        return Optional.of(new ProdutoLoja(
                Loja.KABUM,
                codigo,
                p.path("title").asString(),
                link(codigo, p.path("friendlyName").asString()),
                p.path("thumbnail").asString(),
                precos.path("price").decimalValue(),
                precos.path("priceWithDiscount").decimalValue(),
                p.path("available").asBoolean(false)));
    }

    private JsonNode pageProps(String html) {
        Matcher m = NEXT_DATA.matcher(html);
        if (!m.find()) {
            return mapper.missingNode();
        }
        JsonNode pageProps = mapper.readTree(m.group(1)).path("props").path("pageProps");
        // Na página de busca, "data" pode vir como texto JSON dentro do JSON
        JsonNode data = pageProps.path("data");
        if (data.isString()) {
            return mapper.createObjectNode().set("data", mapper.readTree(data.asString()));
        }
        return pageProps;
    }

    private synchronized Optional<String> baixar(String url) {
        try {
            aguardarIntervalo();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(Duration.ofSeconds(20))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("KaBuM! respondeu {} para {}", res.statusCode(), url);
                return Optional.empty();
            }
            return Optional.of(res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Falha ao acessar {}: {}", url, e.getMessage());
            return Optional.empty();
        } finally {
            ultimaRequisicao = System.currentTimeMillis();
        }
    }

    private void aguardarIntervalo() throws InterruptedException {
        long espera = ultimaRequisicao + intervaloMillis - System.currentTimeMillis();
        if (espera > 0) {
            Thread.sleep(espera);
        }
    }

    private static String link(String codigo, String friendlyName) {
        return BASE + "/produto/" + codigo + (friendlyName.isBlank() ? "" : "/" + friendlyName);
    }

    static String slug(String termo) {
        String semAcento = Normalizer.normalize(termo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
