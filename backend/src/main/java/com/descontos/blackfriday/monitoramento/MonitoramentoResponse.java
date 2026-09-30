package com.descontos.blackfriday.monitoramento;

import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.notificacao.ResultadoAviso;

import java.math.BigDecimal;
import java.time.Instant;

public record MonitoramentoResponse(
        Long id,
        Loja loja,
        String lojaNome,
        String codigoProduto,
        String nome,
        String link,
        String imagem,
        BigDecimal precoMaximo,
        BigDecimal precoAtual,
        BigDecimal precoPixAtual,
        boolean disponivel,
        BigDecimal precoNotificado,
        ResultadoAviso ultimoAvisoResultado,
        Instant ultimoAvisoEm,
        Instant criadoEm,
        Instant ultimaVerificacao
) {
    static MonitoramentoResponse de(ProdutoMonitorado m) {
        return new MonitoramentoResponse(m.getId(), m.getLoja(), m.getLoja().getNome(), m.getCodigoProduto(),
                m.getNome(), m.getLink(), m.getImagem(), m.getPrecoMaximo(), m.getPrecoAtual(),
                m.getPrecoPixAtual(), m.isDisponivel(), m.getPrecoNotificado(), m.getUltimoAvisoResultado(),
                m.getUltimoAvisoEm(), m.getCriadoEm(), m.getUltimaVerificacao());
    }
}
