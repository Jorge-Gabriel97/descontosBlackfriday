package com.descontos.blackfriday.config;

import jakarta.persistence.AttributeConverter;

/**
 * Grava o enum como texto simples. Com {@code @Enumerated}, o H2 cria uma coluna ENUM que o
 * {@code ddl-auto=update} não altera, e um valor novo no enum passaria a ser recusado pelo banco.
 */
public abstract class EnumComoTexto<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> tipo;

    protected EnumComoTexto(Class<E> tipo) {
        this.tipo = tipo;
    }

    @Override
    public String convertToDatabaseColumn(E valor) {
        return valor == null ? null : valor.name();
    }

    @Override
    public E convertToEntityAttribute(String valor) {
        return valor == null ? null : Enum.valueOf(tipo, valor);
    }
}
