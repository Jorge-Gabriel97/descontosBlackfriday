package com.descontos.blackfriday.notificacao;

import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.loja.ProdutoLoja;
import com.descontos.blackfriday.monitoramento.ProdutoMonitorado;
import com.descontos.blackfriday.usuario.Usuario;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class NotificadorEmailTest {

    private final ProdutoLoja produto = new ProdutoLoja(Loja.KABUM, "541147", "Cafeteira Mondial",
            "https://www.kabum.com.br/produto/541147/cafeteira-mondial", "",
            new BigDecimal("249.90"), new BigDecimal("237.41"), true);

    private ProdutoMonitorado monitorado() {
        Usuario usuario = new Usuario("Jorge", "Cliente@Exemplo.com ", "hash");
        ProdutoMonitorado m = new ProdutoMonitorado(usuario, produto, new BigDecimal("240"));
        m.registrarPreco(produto);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provedor(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    @Test
    void enviaParaOEmailDaContaDoUsuario() {
        JavaMailSender sender = mock(JavaMailSender.class);

        ResultadoAviso resultado = new NotificadorEmail(provedor(sender), "app@exemplo.com").notificar(monitorado());

        assertThat(resultado).isEqualTo(ResultadoAviso.ENVIADO);
        verify(sender).send(argThat((SimpleMailMessage msg) ->
                msg.getTo()[0].equals("cliente@exemplo.com")
                        && msg.getFrom().equals("app@exemplo.com")
                        && msg.getSubject().contains("Cafeteira Mondial")));
    }

    @Test
    void semSmtpConfiguradoInformaQueNaoEnviou() {
        NotificadorEmail notificador = new NotificadorEmail(provedor(null), "app@exemplo.com");

        assertThat(notificador.configurado()).isFalse();
        assertThat(notificador.notificar(monitorado())).isEqualTo(ResultadoAviso.EMAIL_NAO_CONFIGURADO);
    }

    @Test
    void falhaDoSmtpEhInformada() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("recusado")).when(sender).send(any(SimpleMailMessage.class));

        ResultadoAviso resultado = new NotificadorEmail(provedor(sender), "app@exemplo.com").notificar(monitorado());

        assertThat(resultado).isEqualTo(ResultadoAviso.FALHOU);
    }

    @Test
    void logDeFalhaMostraARespostaRealDoServidorSmtp(CapturedOutput saida) throws Exception {
        try (ServerSocket servidor = new ServerSocket(0)) {
            Thread smtpFalso = new Thread(() -> responderComIpNaoAutorizado(servidor));
            smtpFalso.start();

            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost("localhost");
            sender.setPort(servidor.getLocalPort());
            sender.setUsername("login-teste");
            sender.setPassword("chave-super-secreta");
            sender.getJavaMailProperties().put("mail.smtp.auth", "true");
            sender.getJavaMailProperties().put("mail.smtp.timeout", "5000");

            ResultadoAviso resultado = new NotificadorEmail(provedor(sender), "app@exemplo.com").notificar(monitorado());
            smtpFalso.join(5000);

            assertThat(resultado).isEqualTo(ResultadoAviso.FALHOU);
            assertThat(saida.getOut())
                    .contains("Falha ao enviar e-mail para cliente@exemplo.com")
                    .contains("Authentication failed")
                    .contains("525 5.7.1 Unauthorized IP address")
                    .doesNotContain("chave-super-secreta");
        }
    }

    private static void responderComIpNaoAutorizado(ServerSocket servidor) {
        try (Socket cliente = servidor.accept();
             BufferedReader entrada = new BufferedReader(new InputStreamReader(cliente.getInputStream(), StandardCharsets.US_ASCII));
             Writer saida = new OutputStreamWriter(cliente.getOutputStream(), StandardCharsets.US_ASCII)) {
            saida.write("220 smtp falso\r\n");
            saida.flush();
            String linha;
            while ((linha = entrada.readLine()) != null) {
                String comando = linha.toUpperCase(Locale.ROOT);
                if (comando.startsWith("EHLO")) {
                    saida.write("250-smtp falso\r\n250 AUTH PLAIN LOGIN\r\n");
                } else if (comando.startsWith("AUTH")) {
                    saida.write("525 5.7.1 Unauthorized IP address\r\n");
                } else if (comando.startsWith("QUIT")) {
                    saida.write("221 tchau\r\n");
                    saida.flush();
                    return;
                } else {
                    saida.write("250 ok\r\n");
                }
                saida.flush();
            }
        } catch (IOException clienteFechouAConexao) {
        }
    }

    @Test
    void descricaoIncluiFalhasPorMensagemDoEnvio() {
        MailSendException envio = new MailSendException(Map.of(new Object(),
                new MessagingException("550 5.7.1 Sender not allowed")));

        assertThat(NotificadorEmail.descreverFalha(envio)).contains("550 5.7.1 Sender not allowed");
    }

    @Test
    void corpoTrazLinkPrecosEAvisoDeIndependencia() {
        String corpo = new NotificadorEmail(provedor(null), "app@exemplo.com").montarCorpo(monitorado());

        assertThat(corpo)
                .contains("Olá, Jorge!")
                .contains("https://www.kabum.com.br/produto/541147/cafeteira-mondial")
                .contains("237,41")
                .contains("249,90")
                .contains("confira o valor atualizado")
                .contains("sem vínculo com o KaBuM!");
    }
}
