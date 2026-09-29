package com.descontos.blackfriday.notificacao;

import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.loja.ProdutoLoja;
import com.descontos.blackfriday.monitoramento.ProdutoMonitorado;
import com.descontos.blackfriday.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
