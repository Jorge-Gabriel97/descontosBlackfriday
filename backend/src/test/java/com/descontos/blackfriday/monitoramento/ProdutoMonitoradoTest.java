package com.descontos.blackfriday.monitoramento;

import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.loja.ProdutoLoja;
import com.descontos.blackfriday.notificacao.ResultadoAviso;
import com.descontos.blackfriday.usuario.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProdutoMonitoradoTest {

    private static ProdutoLoja produto(String precoPix, boolean disponivel) {
        return new ProdutoLoja(Loja.KABUM, "1", "Fone", "https://www.kabum.com.br/produto/1", "",
                new BigDecimal(precoPix).add(BigDecimal.TEN), new BigDecimal(precoPix), disponivel);
    }

    private final ProdutoMonitorado monitorado = new ProdutoMonitorado(
            new Usuario("Cliente", "cliente@exemplo.com", "hash"), produto("300", true), new BigDecimal("200"));

    private boolean verificar(String precoPix, ResultadoAviso resultado) {
        boolean avisar = monitorado.registrarPreco(produto(precoPix, true));
        if (avisar) {
            monitorado.registrarAviso(resultado);
        }
        return avisar;
    }

    private boolean verificar(String precoPix) {
        return verificar(precoPix, ResultadoAviso.ENVIADO);
    }

    @Test
    void naoAvisaAcimaDoPrecoMaximo() {
        assertThat(verificar("250")).isFalse();
    }

    @Test
    void avisaQuandoChegaAoPrecoMaximo() {
        assertThat(verificar("200")).isTrue();
        assertThat(monitorado.getPrecoNotificado()).isEqualByComparingTo("200");
    }

    @Test
    void naoRepeteAvisoParaOMesmoPreco() {
        verificar("190");

        assertThat(verificar("190")).isFalse();
        assertThat(verificar("195")).isFalse();
    }

    @Test
    void avisaDeNovoSeOPrecoCairMais() {
        verificar("190");

        assertThat(verificar("180")).isTrue();
    }

    @Test
    void avisaDeNovoDepoisDeSubirEVoltarACair() {
        verificar("190");
        verificar("250");

        assertThat(verificar("190")).isTrue();
    }

    @Test
    void naoAvisaProdutoIndisponivel() {
        assertThat(monitorado.registrarPreco(produto("150", false))).isFalse();
    }

    @Test
    void tentaDeNovoQuandoOEmailNaoSaiu() {
        verificar("190", ResultadoAviso.FALHOU);
        assertThat(monitorado.getPrecoNotificado()).isNull();
        assertThat(monitorado.getUltimoAvisoResultado()).isEqualTo(ResultadoAviso.FALHOU);

        assertThat(verificar("190")).isTrue();
        assertThat(monitorado.getUltimoAvisoResultado()).isEqualTo(ResultadoAviso.ENVIADO);
    }

    @Test
    void tentaDeNovoQuandoEmailNaoEstaConfigurado() {
        verificar("190", ResultadoAviso.EMAIL_NAO_CONFIGURADO);

        assertThat(verificar("190")).isTrue();
    }
}
