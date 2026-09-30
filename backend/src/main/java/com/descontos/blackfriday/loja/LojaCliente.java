package com.descontos.blackfriday.loja;

import java.util.List;
import java.util.Optional;

/** Para ativar uma loja, crie um {@code @Component} que implemente esta interface. */
public interface LojaCliente {

    Loja loja();

    List<ProdutoLoja> buscar(String termo);

    Optional<ProdutoLoja> consultar(String codigo);
}
