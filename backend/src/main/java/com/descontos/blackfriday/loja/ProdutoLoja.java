package com.descontos.blackfriday.loja;

import java.math.BigDecimal;

/** {@code precoPix} é o menor preço à vista; é ele que decide o aviso. */
public record ProdutoLoja(
        Loja loja,
        String codigo,
        String nome,
        String link,
        String imagem,
        BigDecimal preco,
        BigDecimal precoPix,
        boolean disponivel
) {
}
