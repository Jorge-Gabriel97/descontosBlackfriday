package com.descontos.blackfriday.loja;

/** Uma loja só fica ativa quando existe um {@link LojaCliente} para ela. */
public enum Loja {
    KABUM("KaBuM!"),
    SHOPEE("Shopee"),
    MERCADO_LIVRE("Mercado Livre"),
    AMAZON("Amazon"),
    ALIEXPRESS("AliExpress");

    private final String nome;

    Loja(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }
}
