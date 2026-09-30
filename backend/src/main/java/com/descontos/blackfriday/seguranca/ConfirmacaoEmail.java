package com.descontos.blackfriday.seguranca;

import com.descontos.blackfriday.notificacao.NotificadorEmail;
import com.descontos.blackfriday.notificacao.ResultadoAviso;
import com.descontos.blackfriday.seguranca.TokenEmail.Finalidade;
import com.descontos.blackfriday.usuario.Usuario;
import com.descontos.blackfriday.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

@Service
public class ConfirmacaoEmail {

    static final Duration VALIDADE = Duration.ofHours(24);
    // Impede usar o app para lotar a caixa de entrada de alguém
    static final Duration INTERVALO_REENVIO = Duration.ofMinutes(1);

    private final TokensEmail tokens;
    private final NotificadorEmail notificador;
    private final UsuarioRepository usuarios;
    private final String urlPublica;

    public ConfirmacaoEmail(TokensEmail tokens, NotificadorEmail notificador, UsuarioRepository usuarios,
                            @Value("${app.url-publica}") String urlPublica) {
        this.tokens = tokens;
        this.notificador = notificador;
        this.usuarios = usuarios;
        this.urlPublica = urlPublica;
    }

    public ResultadoAviso enviar(Usuario usuario) {
        String token = tokens.gerar(usuario, Finalidade.CONFIRMAR_EMAIL, VALIDADE);
        return notificador.enviarConfirmacao(usuario, urlPublica + "/api/auth/confirmar-email?token=" + token);
    }

    public ResultadoAviso reenviar(Long usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (usuario.isEmailConfirmado()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seu e-mail já está confirmado");
        }
        if (tokens.enviadoHaMenosDe(usuarioId, Finalidade.CONFIRMAR_EMAIL, INTERVALO_REENVIO)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Acabamos de enviar um e-mail. Aguarde um minuto para pedir outro.");
        }
        return enviar(usuario);
    }

    @Transactional
    public Optional<Long> confirmar(String token) {
        return tokens.usar(token, Finalidade.CONFIRMAR_EMAIL)
                .flatMap(u -> usuarios.findById(u.getId()))
                .map(u -> {
                    u.confirmarEmail();
                    return u.getId();
                });
    }

    public String urlPublica() {
        return urlPublica;
    }
}
