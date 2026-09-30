package com.descontos.blackfriday.kabum;

import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.loja.LojaCliente;
import com.descontos.blackfriday.loja.ProdutoLoja;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê o JSON {@code __NEXT_DATA__} das páginas públicas do KaBuM!. Respeita o robots.txt: só /busca e
 * /produto, sem parâmetros de URL, User-Agent próprio e intervalo entre todas as requisições.
 */
@Component
public class KabumCliente implements LojaCliente {

    private static final Logger log = LoggerFactory.getLogger(KabumCliente.class);
    private static final String BASE = "https://www.kabum.com.br";
    private static final Pattern NEXT_DATA =
            Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);
    private static final Pattern CODIGO_VALIDO = Pattern.compile("\\d{1,12}");
    private static final int MAX_REDIRECIONAMENTOS = 3;
    private static final Set<Integer> REDIRECIONAMENTOS = Set.of(301, 302, 303, 307, 308);
    // Bloqueados no robots.txt
    private static final List<String> CAMINHOS_PROIBIDOS = List.of(
            "/precarrinho", "/carrinho", "/minha-conta", "/login", "/kabum3/", "/manager/",
            "/destaques", "/lancamentos", "/cgi-local", "/link", "/conteudo/descricao/");
    static final String BUSCA_FALHOU = "O KaBuM! não respondeu à busca agora. Tente de novo em instantes.";

    // Redirecionamentos à mão, para tirar os parâmetros e checar o destino
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String userAgent;
    private final long intervaloMillis;
    private final String endereco;
    private long ultimaRequisicao;

    @Autowired
    public KabumCliente(@Value("${app.kabum.user-agent}") String userAgent,
                        @Value("${app.kabum.intervalo-entre-requisicoes}") Duration intervalo) {
        this(userAgent, intervalo, BASE);
    }

    KabumCliente(String userAgent, Duration intervalo, String endereco) {
        this.userAgent = userAgent;
        this.intervaloMillis = intervalo.toMillis();
        this.endereco = endereco;
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
        String html = baixar("/busca/" + slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, BUSCA_FALHOU));
        if (!NEXT_DATA.matcher(html).find()) {
            log.warn("Página de busca do KaBuM! sem os dados esperados (__NEXT_DATA__) para \"{}\"", slug);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, BUSCA_FALHOU);
        }
        return extrairBusca(html);
    }

    @Override
    public Optional<ProdutoLoja> consultar(String codigo) {
        if (codigo == null || !CODIGO_VALIDO.matcher(codigo).matches()) {
            return Optional.empty();
        }
        return baixar("/produto/" + codigo).flatMap(this::extrairProduto);
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
        // Na busca, "data" vem como texto JSON dentro do JSON
        JsonNode data = pageProps.path("data");
        if (data.isString()) {
            return mapper.createObjectNode().set("data", mapper.readTree(data.asString()));
        }
        return pageProps;
    }

    private synchronized Optional<String> baixar(String caminho) {
        for (int i = 0; i <= MAX_REDIRECIONAMENTOS; i++) {
            String url = endereco + caminho;
            HttpResponse<String> res;
            try {
                res = requisitar(url);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (IOException e) {
                log.warn("Falha ao acessar {}: {}", url, e.getMessage());
                return Optional.empty();
            }
            if (res.statusCode() == 200) {
                return Optional.of(res.body());
            }
            if (!REDIRECIONAMENTOS.contains(res.statusCode())) {
                log.warn("KaBuM! respondeu {} para {}", res.statusCode(), url);
                return Optional.empty();
            }
            String destino = res.headers().firstValue("Location").orElse("");
            Optional<String> proximo = caminhoPermitido(destino);
            if (proximo.isEmpty()) {
                log.warn("KaBuM! redirecionou {} para um endereço que o app não acessa: {}", url, destino);
                return Optional.empty();
            }
            caminho = proximo.get();
        }
        log.warn("KaBuM! redirecionou mais de {} vezes a partir de {}", MAX_REDIRECIONAMENTOS, caminho);
        return Optional.empty();
    }

    private HttpResponse<String> requisitar(String url) throws IOException, InterruptedException {
        try {
            aguardarIntervalo();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(Duration.ofSeconds(20))
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } finally {
            ultimaRequisicao = System.currentTimeMillis();
        }
    }

    static Optional<String> caminhoPermitido(String destino) {
        if (destino.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(BASE + "/").resolve(destino.strip());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String caminho = uri.getRawPath();
        if (!"https".equals(uri.getScheme()) || !"www.kabum.com.br".equals(uri.getHost())
                || caminho == null || !caminho.startsWith("/") || caminho.contains("..")
                || CAMINHOS_PROIBIDOS.stream().anyMatch(caminho::startsWith)) {
            return Optional.empty();
        }
        return Optional.of(caminho);
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
