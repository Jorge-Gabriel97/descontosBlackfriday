package com.descontos.blackfriday.seguranca;

import com.descontos.blackfriday.seguranca.TokenEmail.Finalidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface TokenEmailRepository extends JpaRepository<TokenEmail, Long> {

    Optional<TokenEmail> findByHashAndFinalidade(String hash, Finalidade finalidade);

    Optional<TokenEmail> findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(Long usuarioId, Finalidade finalidade);

    void deleteByUsuarioIdAndFinalidade(Long usuarioId, Finalidade finalidade);

    void deleteByExpiraEmBefore(Instant instante);
}
