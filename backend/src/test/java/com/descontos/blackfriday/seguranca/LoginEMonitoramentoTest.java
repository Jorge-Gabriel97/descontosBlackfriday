package com.descontos.blackfriday.seguranca;

import com.descontos.blackfriday.kabum.KabumCliente;
import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.loja.ProdutoLoja;
import com.descontos.blackfriday.notificacao.NotificadorEmail;
import com.jayway.jsonpath.JsonPath;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:login;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class LoginEMonitoramentoTest {

    @Autowired
    MockMvc mvc;

    // Métodos reais porque loja() é lido na criação do contexto; consultar() é trocado nos testes
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    KabumCliente kabum;

    @MockitoSpyBean
    NotificadorEmail notificador;

    @BeforeEach
    void kabumFalso() {
        doAnswer(inv -> Optional.of(new ProdutoLoja(Loja.KABUM,
                inv.getArgument(0), "Fone JBL", "https://www.kabum.com.br/produto/1", "",
                new BigDecimal("322.11"), new BigDecimal("289.90"), true)))
                .when(kabum).consultar(anyString());
    }

    private MockHttpSession cadastrar(String email) throws Exception {
        MockHttpSession sessao = cadastrarSemConfirmar(email);
        mvc.perform(get(linkDeConfirmacao(email))).andExpect(status().isFound());
        return sessao;
    }

    private MockHttpSession cadastrarSemConfirmar(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Teste\",\"email\":\"" + email + "\",\"senha\":\"senha-forte-123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andReturn();
        return (MockHttpSession) res.getRequest().getSession(false);
    }

    private String linkDeConfirmacao(String email) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificador, atLeastOnce())
                .enviarConfirmacao(argThat(u -> u.getEmail().equals(email.toLowerCase())), link.capture());
        return link.getValue().replace("http://localhost:5173", "");
    }

    private void monitorar(MockHttpSession sessao, String codigo) throws Exception {
        mvc.perform(post("/api/monitoramentos").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loja\":\"KABUM\",\"codigoProduto\":\"" + codigo + "\",\"precoMaximo\":300}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ultimoAvisoResultado").value("EMAIL_NAO_CONFIGURADO"));
    }

    @Test
    void semLoginAApiRecusaComStatus401() throws Exception {
        mvc.perform(get("/api/monitoramentos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/lojas/KABUM/busca").param("termo", "fone")).andExpect(status().isUnauthorized());
    }

    @Test
    void statusEhPublicoEListaAsLojas() throws Exception {
        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailConfigurado").value(false))
                .andExpect(jsonPath("$.lojas[?(@.id=='KABUM')].ativa").value(true))
                .andExpect(jsonPath("$.lojas[?(@.id=='AMAZON')].ativa").value(false));
    }

    @Test
    void semTokenCsrfOCadastroEhRecusado() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"email\":\"csrf@exemplo.com\",\"senha\":\"senha-forte-123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadastroLoginELogout() throws Exception {
        MockHttpSession sessao = cadastrar("Fluxo@Exemplo.com");
        mvc.perform(get("/api/auth/eu").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("fluxo@exemplo.com"));

        mvc.perform(post("/api/auth/logout").session(sessao).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/eu").session(sessao)).andExpect(status().isUnauthorized());

        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"FLUXO@exemplo.com\",\"senha\":\"senha-forte-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        mvc.perform(get("/api/auth/eu").session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk());
    }

    @Test
    void senhaErradaEEmailRepetidoSaoRecusados() throws Exception {
        cadastrar("repetido@exemplo.com");

        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"repetido@exemplo.com\",\"senha\":\"errada-123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));

        mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"email\":\"repetido@exemplo.com\",\"senha\":\"senha-forte-123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void contaNaoConfirmadaRetemOAvisoAteConfirmarOEmail() throws Exception {
        MockHttpSession sessao = cadastrarSemConfirmar("nova@exemplo.com");
        mvc.perform(get("/api/auth/eu").session(sessao)).andExpect(jsonPath("$.emailConfirmado").value(false));

        MvcResult criado = mvc.perform(post("/api/monitoramentos").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loja\":\"KABUM\",\"codigoProduto\":\"77\",\"precoMaximo\":300}"))
                .andExpect(jsonPath("$.ultimoAvisoResultado").value("EMAIL_NAO_CONFIRMADO"))
                .andReturn();
        Number id = JsonPath.read(criado.getResponse().getContentAsString(), "$.id");

        mvc.perform(get(linkDeConfirmacao("nova@exemplo.com")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/?email=confirmado"));
        mvc.perform(get("/api/auth/eu").session(sessao)).andExpect(jsonPath("$.emailConfirmado").value(true));
        // O aviso retido sai na hora da confirmação (aqui sem SMTP, então fica como não configurado)
        mvc.perform(get("/api/monitoramentos").session(sessao))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].ultimoAvisoResultado").value("EMAIL_NAO_CONFIGURADO"));
    }

    @Test
    void linkDeConfirmacaoSoFuncionaUmaVez() throws Exception {
        cadastrarSemConfirmar("umavez@exemplo.com");
        String link = linkDeConfirmacao("umavez@exemplo.com");

        mvc.perform(get(link)).andExpect(header().string("Location", "http://localhost:5173/?email=confirmado"));
        mvc.perform(get(link)).andExpect(header().string("Location", "http://localhost:5173/?email=link-invalido"));
        mvc.perform(get("/api/auth/confirmar-email").param("token", "inventado"))
                .andExpect(header().string("Location", "http://localhost:5173/?email=link-invalido"));
    }

    @Test
    void reenvioDaConfirmacaoTemIntervaloMinimoEPrecisaDeContaNaoConfirmada() throws Exception {
        MockHttpSession nova = cadastrarSemConfirmar("reenvio@exemplo.com");
        mvc.perform(post("/api/auth/reenviar-confirmacao").session(nova).with(csrf()))
                .andExpect(status().isTooManyRequests());

        MockHttpSession confirmada = cadastrar("jaconfirmada@exemplo.com");
        mvc.perform(post("/api/auth/reenviar-confirmacao").session(confirmada).with(csrf()))
                .andExpect(status().isConflict());
    }

    private MockHttpServletRequestBuilder esqueciSenha(String email) {
        return post("/api/auth/esqueci-senha").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}");
    }

    private MockHttpServletRequestBuilder redefinirSenha(String token, String senha) {
        return post("/api/auth/redefinir-senha").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"senha\":\"" + senha + "\"}");
    }

    // O pedido roda em segundo plano, por isso a espera
    private String tokenDeRedefinicao(String email) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificador, timeout(5000))
                .enviarRedefinicaoSenha(argThat(u -> u.getEmail().equals(email)), link.capture());
        return link.getValue().substring(link.getValue().indexOf("redefinir=") + "redefinir=".length());
    }

    @Test
    void esqueciASenhaTrocaASenhaComLinkDeUsoUnico() throws Exception {
        cadastrar("esqueci@exemplo.com");
        mvc.perform(esqueciSenha("ESQUECI@exemplo.com")).andExpect(status().isNoContent());
        String token = tokenDeRedefinicao("esqueci@exemplo.com");

        mvc.perform(redefinirSenha(token, "nova-senha-456")).andExpect(status().isNoContent());

        mvc.perform(login("esqueci@exemplo.com", "senha-forte-123", "10.1.0.1")).andExpect(status().isUnauthorized());
        mvc.perform(login("esqueci@exemplo.com", "nova-senha-456", "10.1.0.2")).andExpect(status().isOk());
        mvc.perform(redefinirSenha(token, "outra-senha-789"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Este link é inválido")));
    }

    @Test
    void esqueciASenhaRespondeIgualParaEmailSemConta() throws Exception {
        mvc.perform(esqueciSenha("sem-conta@exemplo.com")).andExpect(status().isNoContent());

        verify(notificador, after(500).never()).enviarRedefinicaoSenha(any(), anyString());
    }

    @Test
    void redefinirSenhaLiberaOBloqueioEConfirmaOEmail() throws Exception {
        cadastrarSemConfirmar("travada@exemplo.com");
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("travada@exemplo.com", "chute-" + i, "10.2.0.1"));
        }
        mvc.perform(login("travada@exemplo.com", "senha-forte-123", "10.2.0.2")).andExpect(status().isTooManyRequests());

        mvc.perform(esqueciSenha("travada@exemplo.com")).andExpect(status().isNoContent());
        mvc.perform(redefinirSenha(tokenDeRedefinicao("travada@exemplo.com"), "nova-senha-456"))
                .andExpect(status().isNoContent());

        MvcResult entrou = mvc.perform(login("travada@exemplo.com", "nova-senha-456", "10.2.0.3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailConfirmado").value(true))
                .andReturn();
        mvc.perform(get("/api/auth/eu").session((MockHttpSession) entrou.getRequest().getSession(false)))
                .andExpect(jsonPath("$.emailConfirmado").value(true));
    }

    @Test
    void novaSenhaCurtaEhRecusada() throws Exception {
        mvc.perform(redefinirSenha("qualquer", "1234567"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Senha: ")));
    }

    @Test
    void cincoSenhasErradasBloqueiamOLoginMesmoComASenhaCerta() throws Exception {
        cadastrar("alvo@exemplo.com");
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("alvo@exemplo.com", "chute-" + i, "10.0.0.1")).andExpect(status().isUnauthorized());
        }

        mvc.perform(login("ALVO@exemplo.com", "senha-forte-123", "10.0.0.2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Muitas tentativas. Tente de novo em 15 minutos."));
        // Mesma resposta para e-mail sem conta: o bloqueio não revela quem está cadastrado
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("ninguem@exemplo.com", "chute-" + i, "10.0.0.3")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("ninguem@exemplo.com", "chute", "10.0.0.4")).andExpect(status().isTooManyRequests());
    }

    private static MockHttpServletRequestBuilder login(String email, String senha, String ip) {
        return post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}")
                .with(req -> {
                    req.setRemoteAddr(ip);
                    return req;
                });
    }

    @Test
    void senhaCurtaEhRecusadaComMensagemDoCampo() throws Exception {
        mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"email\":\"curta@exemplo.com\",\"senha\":\"1234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Senha: ")));
    }

    @Test
    void lojaInexistenteNaUrlEhRecusada() throws Exception {
        MockHttpSession sessao = cadastrar("url@exemplo.com");
        mvc.perform(get("/api/lojas/NAO_EXISTE/busca").param("termo", "fone").session(sessao))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadaUsuarioSoVeEAlteraOsProprios() throws Exception {
        MockHttpSession ana = cadastrar("ana@exemplo.com");
        MockHttpSession bia = cadastrar("bia@exemplo.com");
        monitorar(ana, "111");
        monitorar(bia, "222");

        MvcResult listaAna = mvc.perform(get("/api/monitoramentos").session(ana))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigoProduto").value("111"))
                .andExpect(jsonPath("$[0].usuario").doesNotExist())
                .andReturn();
        String idAna = listaAna.getResponse().getContentAsString().replaceAll(".*?\"id\":(\\d+).*", "$1");

        mvc.perform(post("/api/monitoramentos/" + idAna + "/verificar").session(bia).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/monitoramentos/" + idAna).session(bia).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/monitoramentos/" + idAna).session(ana).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void lojaAindaNaoDisponivelEhRecusada() throws Exception {
        MockHttpSession sessao = cadastrar("loja@exemplo.com");
        mvc.perform(get("/api/lojas/AMAZON/busca").param("termo", "fone").session(sessao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amazon ainda não está disponível"));
    }
}
