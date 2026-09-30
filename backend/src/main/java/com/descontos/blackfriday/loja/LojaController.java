package com.descontos.blackfriday.loja;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lojas")
public class LojaController {

    private final Lojas lojas;

    public LojaController(Lojas lojas) {
        this.lojas = lojas;
    }

    @GetMapping("/{loja}/busca")
    public List<ProdutoLoja> buscar(@PathVariable Loja loja, @RequestParam @NotBlank @Size(max = 100) String termo) {
        return lojas.cliente(loja).buscar(termo);
    }
}
