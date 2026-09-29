package com.descontos.blackfriday.notificacao;

import com.descontos.blackfriday.monitoramento.ProdutoMonitorado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Avisa o usuário, no e-mail da conta dele, que o produto chegou ao preço desejado.
 *
 * <p>Segue as políticas das lojas (no KaBuM!, o tópico "Oferta"): o e-mail deixa claro
 * que não é da loja, informa quando o preço foi lido e manda conferir o preço no link.
 */
@Component
public class NotificadorEmail {

    private static final Logger log = LoggerFactory.getLogger(NotificadorEmail.class);
    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remetente;

    public NotificadorEmail(ObjectProvider<JavaMailSender> mailSender,
                            @Value("${app.mail.remetente}") String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    public boolean configurado() {
        return mailSender.getIfAvailable() != null;
    }

    public ResultadoAviso notificar(ProdutoMonitorado produto) {
        String destino = produto.getUsuario().getEmail();
        String assunto = "[Descontos Black Friday] Preço caiu: " + produto.getNome();
        String corpo = montarCorpo(produto);

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("E-mail não configurado; aviso que seria enviado para {}:\n{}\n\n{}", destino, assunto, corpo);
            return ResultadoAviso.EMAIL_NAO_CONFIGURADO;
        }

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(remetente);
        msg.setTo(destino);
        msg.setSubject(assunto);
        msg.setText(corpo);
        try {
            sender.send(msg);
            log.info("Aviso do produto {} enviado para {}", produto.getCodigoProduto(), destino);
            return ResultadoAviso.ENVIADO;
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail para {}: {}", destino, e.getMessage());
            return ResultadoAviso.FALHOU;
        }
    }

    String montarCorpo(ProdutoMonitorado p) {
        String loja = p.getLoja().getNome();
        return """
                Olá, %s!

                O produto que você está monitorando chegou ao preço que você definiu:

                %s

                Melhor preço (à vista): %s
                Preço normal:           %s
                Seu preço máximo:       %s

                Ver no %s: %s

                Preço lido no site em %s. Os preços mudam com frequência e só valem
                os que aparecem na página do produto: confira o valor atualizado
                no link antes de comprar.

                —
                Aviso enviado pelo Descontos Black Friday, um monitor de preços independente,
                sem vínculo com o %s. Para parar de receber, remova o produto no app.
                """.formatted(
                p.getUsuario().getNome(),
                p.getNome(),
                MOEDA.format(p.getPrecoPixAtual()),
                MOEDA.format(p.getPrecoAtual()),
                MOEDA.format(p.getPrecoMaximo()),
                loja, p.getLink(),
                DATA_HORA.format(p.getUltimaVerificacao()),
                loja);
    }
}
