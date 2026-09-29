package com.descontos.blackfriday.loja;

import java.math.BigDecimal;

/**
 * Produto como aparece no site de uma loja.
 *
 * @param codigo   identificador do produto na loja (ex.: código KaBuM!, ASIN da Amazon)
 * @param preco    preço normal (cartão/boleto)
 * @param precoPix menor preço à vista (no KaBuM!, o preço no PIX); usado para decidir o aviso
 */
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
