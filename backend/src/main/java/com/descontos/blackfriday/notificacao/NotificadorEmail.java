package com.descontos.blackfriday.notificacao;

import com.descontos.blackfriday.monitoramento.ProdutoMonitorado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
            log.error("Falha ao enviar e-mail para {}: {}", destino, descreverFalha(e));
            log.debug("Detalhes da falha de e-mail", e);
            return ResultadoAviso.FALHOU;
        }
    }

    /**
     * Junta as mensagens de toda a cadeia de causas. O Spring embrulha a resposta do servidor
     * SMTP (ex.: "525 5.7.1 Unauthorized IP address") numa exceção de texto genérico,
     * como "Authentication failed"; sem descer até a causa, o motivo real se perde.
     * As exceções do JavaMail trazem só a resposta do servidor, nunca a senha ou a chave.
     */
    static String descreverFalha(Throwable erro) {
        List<String> partes = new ArrayList<>();
        Set<Throwable> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pendentes = new ArrayDeque<>(List.of(erro));
        while (!pendentes.isEmpty()) {
            Throwable atual = pendentes.poll();
            if (!vistos.add(atual)) {
                continue;
            }
            String mensagem = atual.getMessage() == null ? "" : atual.getMessage().strip().replaceAll("\\s+", " ");
            String parte = atual.getClass().getSimpleName() + (mensagem.isEmpty() ? "" : ": " + mensagem);
            if (partes.stream().noneMatch(p -> p.contains(mensagem) && !mensagem.isEmpty())) {
                partes.add(parte);
            }
            // Falhas de envio ficam por mensagem, fora da cadeia de causas
            if (atual instanceof MailSendException envio) {
                pendentes.addAll(Arrays.asList(envio.getMessageExceptions()));
            }
            if (atual.getCause() != null) {
                pendentes.add(atual.getCause());
            }
        }
        return String.join(" <- ", partes);
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
