package com.descontos.blackfriday.loja;

/**
 * Lojas conhecidas pelo app. Uma loja só fica ativa quando existe um
 * {@link LojaCliente} para ela; as demais aparecem como "em breve".
 */
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
