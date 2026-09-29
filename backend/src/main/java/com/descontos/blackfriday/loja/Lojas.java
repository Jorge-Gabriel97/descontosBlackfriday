package com.descontos.blackfriday.loja;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Encontra o cliente de cada loja ativa. */
@Component
public class Lojas {

    private final Map<Loja, LojaCliente> clientes = new EnumMap<>(Loja.class);

    public Lojas(List<LojaCliente> clientes) {
        clientes.forEach(c -> this.clientes.put(c.loja(), c));
    }

    public LojaCliente cliente(Loja loja) {
        LojaCliente cliente = clientes.get(loja);
        if (cliente == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, loja.getNome() + " ainda não está disponível");
        }
        return cliente;
    }

    public boolean ativa(Loja loja) {
        return clientes.containsKey(loja);
    }
}
