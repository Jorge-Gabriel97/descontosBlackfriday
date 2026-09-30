package com.descontos.blackfriday.seguranca;

import com.descontos.blackfriday.seguranca.TokenEmail.Finalidade;
import com.descontos.blackfriday.usuario.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class TokensEmail {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final TokenEmailRepository tokens;
    private final Clock relogio;

    @Autowired
    public TokensEmail(TokenEmailRepository tokens) {
        this(tokens, Clock.systemUTC());
    }

    TokensEmail(TokenEmailRepository tokens, Clock relogio) {
        this.tokens = tokens;
        this.relogio = relogio;
    }

    /** Gera um novo token e invalida os anteriores da mesma finalidade. */
    @Transactional
    public String gerar(Usuario usuario, Finalidade finalidade, Duration validade) {
        Instant agora = relogio.instant();
        tokens.deleteByUsuarioIdAndFinalidade(usuario.getId(), finalidade);
        tokens.deleteByExpiraEmBefore(agora);

        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new TokenEmail(usuario, finalidade, hash(token), agora, agora.plus(validade)));
        return token;
    }

    /** Consome o token: vale uma vez só e dentro da validade. */
    @Transactional
    public Optional<Usuario> usar(String token, Finalidade finalidade) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByHashAndFinalidade(hash(token), finalidade).flatMap(t -> {
            tokens.delete(t);
            return relogio.instant().isBefore(t.getExpiraEm()) ? Optional.of(t.getUsuario()) : Optional.empty();
        });
    }

    public boolean enviadoHaMenosDe(Long usuarioId, Finalidade finalidade, Duration intervalo) {
        return tokens.findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(usuarioId, finalidade)
                .map(t -> relogio.instant().isBefore(t.getCriadoEm().plus(intervalo)))
                .orElse(false);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
