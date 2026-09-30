package com.descontos.blackfriday.seguranca;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteDeTentativasTest {

    private final RelogioDeTeste relogio = new RelogioDeTeste();
    private final LimiteDeTentativas limite = new LimiteDeTentativas(5, 20, Duration.ofMinutes(15), relogio);

    private void falhar(int vezes, String email, String ip) {
        for (int i = 0; i < vezes; i++) {
            limite.registrarFalha(email, ip);
        }
    }

    @Test
    void bloqueiaOEmailNaQuintaFalhaELiberaDepoisDe15Minutos() {
        falhar(4, "ana@exemplo.com", "1.1.1.1");
        assertThat(limite.bloqueado("ana@exemplo.com", "1.1.1.1")).isFalse();

        falhar(1, "ana@exemplo.com", "1.1.1.1");
        assertThat(limite.bloqueado("ana@exemplo.com", "9.9.9.9")).isTrue();

        relogio.avancar(Duration.ofMinutes(14));
        assertThat(limite.bloqueado("ana@exemplo.com", "9.9.9.9")).isTrue();

        relogio.avancar(Duration.ofMinutes(1));
        assertThat(limite.bloqueado("ana@exemplo.com", "9.9.9.9")).isFalse();
    }

    @Test
    void bloqueiaOIpQueTentaMuitasContas() {
        for (int i = 0; i < 20; i++) {
            falhar(1, "conta" + i + "@exemplo.com", "6.6.6.6");
        }

        assertThat(limite.bloqueado("outra@exemplo.com", "6.6.6.6")).isTrue();
        assertThat(limite.bloqueado("outra@exemplo.com", "7.7.7.7")).isFalse();
    }

    @Test
    void falhasAntigasExpiramSemBloquear() {
        falhar(4, "bia@exemplo.com", "2.2.2.2");
        relogio.avancar(Duration.ofMinutes(16));
        falhar(1, "bia@exemplo.com", "2.2.2.2");

        assertThat(limite.bloqueado("bia@exemplo.com", "2.2.2.2")).isFalse();
    }

    @Test
    void loginCertoZeraOEmailMasNaoOIp() {
        falhar(4, "caio@exemplo.com", "3.3.3.3");
        limite.registrarSucesso("caio@exemplo.com");
        falhar(4, "caio@exemplo.com", "3.3.3.3");
        assertThat(limite.bloqueado("caio@exemplo.com", "4.4.4.4")).isFalse();

        falhar(12, "outro@exemplo.com", "3.3.3.3");
        assertThat(limite.bloqueado("qualquer@exemplo.com", "3.3.3.3")).isTrue();
    }
}
