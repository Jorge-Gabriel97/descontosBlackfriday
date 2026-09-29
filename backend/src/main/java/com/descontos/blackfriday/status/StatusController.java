package com.descontos.blackfriday.status;

import com.descontos.blackfriday.loja.Loja;
import com.descontos.blackfriday.loja.Lojas;
import com.descontos.blackfriday.notificacao.NotificadorEmail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Informações públicas do servidor que a tela usa para se ajustar. */
@RestController
public class StatusController {

    public record LojaStatus(Loja id, String nome, boolean ativa) {
    }

    public record Status(boolean emailConfigurado, List<LojaStatus> lojas) {
    }

    private final NotificadorEmail notificador;
    private final Lojas lojas;

    public StatusController(NotificadorEmail notificador, Lojas lojas) {
        this.notificador = notificador;
        this.lojas = lojas;
    }

    @GetMapping("/api/status")
    public Status status() {
        List<LojaStatus> todas = Arrays.stream(Loja.values())
                .map(l -> new LojaStatus(l, l.getNome(), lojas.ativa(l)))
                .toList();
        return new Status(notificador.configurado(), todas);
    }
}
