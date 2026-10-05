package com.descontos.blackfriday;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe o app num PostgreSQL de verdade, como em produção: as migrações do Flyway precisam bater com as
 * entidades ({@code ddl-auto=validate}) e a sessão de login precisa ficar gravada no banco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Religa o Spring Session, que os outros testes desligam
        properties = "spring.autoconfigure.exclude=org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration")
class PostgresTest {

    private static EmbeddedPostgres postgres;

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) throws IOException {
        postgres = EmbeddedPostgres.start();
        registro.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registro.add("spring.datasource.username", () -> "postgres");
    }

    @LocalServerPort
    int porta;

    @Autowired
    JdbcTemplate jdbc;

    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

    @Test
    void migracoesCriamAsTabelasNoPostgres() {
        List<String> versoes = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);

        assertThat(versoes).containsExactly("1", "2");
    }

    @Test
    void sessaoDoLoginFicaGravadaNoBancoSemOHashDaSenha() throws Exception {
        assertThat(enviar("GET", "/api/status", null).statusCode()).isEqualTo(200);

        HttpResponse<String> cadastro = enviar("POST", "/api/auth/cadastro",
                "{\"nome\":\"Ana\",\"email\":\"ana@exemplo.com\",\"senha\":\"senha-forte-123\"}");
        assertThat(cadastro.statusCode()).isEqualTo(201);
        assertThat(cookie("SESSION").isHttpOnly()).isTrue();

        HttpResponse<String> eu = enviar("GET", "/api/auth/eu", null);
        assertThat(eu.statusCode()).isEqualTo(200);
        assertThat(eu.body()).contains("ana@exemplo.com");

        Map<String, Object> sessao = jdbc.queryForMap(
                "select primary_id, max_inactive_interval from spring_session where principal_name = ?",
                "ana@exemplo.com");
        assertThat(sessao.get("max_inactive_interval")).isEqualTo(7 * 24 * 60 * 60);

        List<byte[]> atributos = jdbc.queryForList(
                "select attribute_bytes from spring_session_attributes where session_primary_id = ?",
                byte[].class, sessao.get("primary_id"));
        assertThat(atributos).isNotEmpty()
                .allSatisfy(bytes -> assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).doesNotContain("{bcrypt}"));

        assertThat(enviar("POST", "/api/auth/logout", null).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from spring_session where principal_name = ?",
                Integer.class, "ana@exemplo.com")).isZero();
    }

    private HttpResponse<String> enviar(String metodo, String caminho, String json) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (!metodo.equals("GET")) {
            req.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN").getValue());
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpCookie cookie(String nome) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(nome))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie " + nome + " não recebido"));
    }
}
