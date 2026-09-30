package com.descontos.blackfriday.monitoramento;

import com.descontos.blackfriday.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/monitoramentos")
public class MonitoramentoController {

    private final MonitoramentoService service;

    public MonitoramentoController(MonitoramentoService service) {
        this.service = service;
    }

    @GetMapping
    public List<MonitoramentoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.listar(usuario.id()).stream().map(MonitoramentoResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonitoramentoResponse cadastrar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                           @Valid @RequestBody MonitoramentoRequest req) {
        return MonitoramentoResponse.de(
                service.cadastrar(usuario.id(), req.loja(), req.codigoProduto(), req.precoMaximo()));
    }

    @PostMapping("/{id}/verificar")
    public MonitoramentoResponse verificar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return MonitoramentoResponse.de(service.verificar(usuario.id(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        service.remover(usuario.id(), id);
    }
}
