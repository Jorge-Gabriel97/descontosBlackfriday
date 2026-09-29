package com.descontos.blackfriday.monitoramento;

import com.descontos.blackfriday.loja.Loja;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record MonitoramentoRequest(
        @NotNull Loja loja,
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,60}", message = "código inválido") String codigoProduto,
        @NotNull @DecimalMin("0.01") BigDecimal precoMaximo
) {
}
