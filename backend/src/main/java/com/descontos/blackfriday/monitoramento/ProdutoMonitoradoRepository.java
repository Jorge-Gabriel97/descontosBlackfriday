package com.descontos.blackfriday.monitoramento;

import com.descontos.blackfriday.loja.Loja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProdutoMonitoradoRepository extends JpaRepository<ProdutoMonitorado, Long> {

    List<ProdutoMonitorado> findAllByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);

    Optional<ProdutoMonitorado> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByUsuarioIdAndLojaAndCodigoProduto(Long usuarioId, Loja loja, String codigoProduto);
}
