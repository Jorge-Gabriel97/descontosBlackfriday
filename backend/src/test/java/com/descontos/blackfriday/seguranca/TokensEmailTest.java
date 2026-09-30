package com.descontos.blackfriday.seguranca;

import com.descontos.blackfriday.seguranca.TokenEmail.Finalidade;
import com.descontos.blackfriday.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TokensEmailTest {

    private final TokenEmailRepository repositorio = mock(TokenEmailRepository.class);
    private final RelogioDeTeste relogio = new RelogioDeTeste();
    private final TokensEmail tokens = new TokensEmail(repositorio, relogio);
    private final Usuario ana = new Usuario("Ana", "ana@exemplo.com", "hash");

    private TokenEmail gerarESalvar(String token) {
        ArgumentCaptor<TokenEmail> salvo = ArgumentCaptor.forClass(TokenEmail.class);
        verify(repositorio).save(salvo.capture());
        when(repositorio.findByHashAndFinalidade(TokensEmail.hash(token), Finalidade.CONFIRMAR_EMAIL))
                .thenReturn(Optional.of(salvo.getValue()));
        return salvo.getValue();
    }

    @Test
    void guardaSoOHashEOTokenValeUmaVez() {
        String token = tokens.gerar(ana, Finalidade.CONFIRMAR_EMAIL, Duration.ofHours(24));
        TokenEmail salvo = gerarESalvar(token);

        assertThat(salvo.getHash()).hasSize(64).isNotEqualTo(token);
        assertThat(tokens.usar(token, Finalidade.CONFIRMAR_EMAIL)).containsSame(ana);
        verify(repositorio).delete(salvo);
    }

    @Test
    void tokenExpiradoNaoFunciona() {
        String token = tokens.gerar(ana, Finalidade.CONFIRMAR_EMAIL, Duration.ofHours(24));
        TokenEmail salvo = gerarESalvar(token);

        relogio.avancar(Duration.ofHours(24));

        assertThat(tokens.usar(token, Finalidade.CONFIRMAR_EMAIL)).isEmpty();
        verify(repositorio).delete(salvo);
    }

    @Test
    void tokenVazioOuDeOutraFinalidadeNaoFunciona() {
        String token = tokens.gerar(ana, Finalidade.CONFIRMAR_EMAIL, Duration.ofHours(24));
        gerarESalvar(token);

        assertThat(tokens.usar("", Finalidade.CONFIRMAR_EMAIL)).isEmpty();
        assertThat(tokens.usar(token, Finalidade.TROCAR_SENHA)).isEmpty();
    }

    @Test
    void novoTokenInvalidaOsAnterioresDaMesmaFinalidade() {
        tokens.gerar(ana, Finalidade.CONFIRMAR_EMAIL, Duration.ofHours(24));

        verify(repositorio).deleteByUsuarioIdAndFinalidade(ana.getId(), Finalidade.CONFIRMAR_EMAIL);
    }
}
