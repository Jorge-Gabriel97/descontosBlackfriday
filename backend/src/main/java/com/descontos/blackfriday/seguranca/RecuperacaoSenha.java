package com.descontos.blackfriday.seguranca;

import com.descontos.blackfriday.notificacao.NotificadorEmail;
import com.descontos.blackfriday.seguranca.TokenEmail.Finalidade;
import com.descontos.blackfriday.usuario.Usuario;
import com.descontos.blackfriday.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class RecuperacaoSenha {

    static final Duration VALIDADE = Duration.ofMinutes(30);
    static final Duration INTERVALO_REENVIO = Duration.ofMinutes(1);

    private final TokensEmail tokens;
    private final NotificadorEmail notificador;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final LimiteDeTentativas limite;
    private final String urlPublica;

    public RecuperacaoSenha(TokensEmail tokens, NotificadorEmail notificador, UsuarioRepository usuarios,
                            PasswordEncoder encoder, LimiteDeTentativas limite,
                            @Value("${app.url-publica}") String urlPublica) {
        this.tokens = tokens;
        this.notificador = notificador;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.limite = limite;
        this.urlPublica = urlPublica;
    }

    // Em segundo plano: a resposta não pode demorar mais quando o e-mail tem conta
    @Async
    public void pedir(String email) {
        usuarios.findByEmail(Usuario.normalizarEmail(email))
                .filter(u -> !tokens.enviadoHaMenosDe(u.getId(), Finalidade.TROCAR_SENHA, INTERVALO_REENVIO))
                .ifPresent(u -> {
                    String token = tokens.gerar(u, Finalidade.TROCAR_SENHA, VALIDADE);
                    notificador.enviarRedefinicaoSenha(u, urlPublica + "/?redefinir=" + token);
                });
    }

    // Quem recebeu o link comprovou ser dono do e-mail: a conta também fica confirmada
    @Transactional
    public boolean redefinir(String token, String novaSenha) {
        return tokens.usar(token, Finalidade.TROCAR_SENHA)
                .flatMap(u -> usuarios.findById(u.getId()))
                .map(u -> {
                    u.trocarSenha(encoder.encode(novaSenha));
                    u.confirmarEmail();
                    limite.registrarSucesso(u.getEmail());
                    return true;
                })
                .orElse(false);
    }
}
