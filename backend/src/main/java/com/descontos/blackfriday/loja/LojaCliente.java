package com.descontos.blackfriday.loja;

import java.util.List;
import java.util.Optional;

/**
 * Acesso a uma loja online. Para ativar uma nova loja (Shopee, Mercado Livre,
 * Amazon, AliExpress...), crie um {@code @Component} que implemente esta interface.
 */
public interface LojaCliente {

    Loja loja();

    /** Busca produtos pelo termo digitado, para o usuário escolher qual quer monitorar. */
    List<ProdutoLoja> buscar(String termo);

    /** Consulta o preço atual de um produto já selecionado. */
    Optional<ProdutoLoja> consultar(String codigo);
}
